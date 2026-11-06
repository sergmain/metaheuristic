#!/usr/bin/env python3
#
# Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, version 3 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
"""
Migration of the Dispatcher's on-disk cache of local Variables to the layout 'v2'.

    before (flat): <variable_dir>/variable-<id>
    after  (v2):   <variable_dir>/v2/<power10>/<power7>/<power4>/variable-<id>

    power4  = id % 10_000
    power7  = id // 10_000 % 1_000
    power10 = id // 10_000_000

This is the split of ai.metaheuristic.commons.utils.DigitUtils.getPower3() and the dir of
ai.metaheuristic.ai.utils.asset.AssetUtils.getVariableCacheDir().
Example: variable-112222 -> v2/0/11/2222/variable-112222

<variable_dir> is ${mh.home}/dispatcher/temp/variable.

Run it while the Dispatcher is stopped:
  1. stop the Dispatcher;
  2. python migrate_variable_cache_v2.py <variable_dir> --dry-run
  3. python migrate_variable_cache_v2.py <variable_dir>
  4. start the Dispatcher built with the v2 layout.

- Only regular files named variable-<digits> directly in <variable_dir> are moved. Everything else - the v2
  dir itself, other names, sub-dirs - is left as it is and counted as skipped.
- A move is a rename inside one volume; content is never copied. The moves run in a thread pool
  (--workers), in batches, so 100k+ files don't go one by one.
- If the v2 target already exists (a Dispatcher with the v2 layout cached it first), the target is kept and
  the flat file is left in place, counted as a conflict; --drop-old-on-conflict deletes the flat file instead.
- Idempotent: a re-run moves only what is still flat.

Exit code: 0 - no errors, 1 - at least one file couldn't be moved, 2 - bad arguments.
"""

import argparse
import os
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from enum import Enum
from typing import Callable, Iterator, NamedTuple, Optional

DEFAULT_VARIABLE_DIR = r'C:\_mh\mhbp_home-PostgreSQL-disk-storage\dispatcher\temp\variable'
V2_DIR = 'v2'
DIV = 10_000
DIV_MIDDLE = 1_000
DEFAULT_BATCH_SIZE = 1_000
FLAT_NAME = re.compile(r'^variable-(\d+)$')


def get_power3(num: int) -> tuple[int, int, int]:
    """(power10, power7, power4) - the same split as DigitUtils.getPower3()."""
    upper, power4 = divmod(num, DIV)
    power10, power7 = divmod(upper, DIV_MIDDLE)
    return power10, power7, power4


def v2_relative_path(variable_id: int, filename: str) -> str:
    power10, power7, power4 = get_power3(variable_id)
    return os.path.join(V2_DIR, str(power10), str(power7), str(power4), filename)


class Outcome(Enum):
    MOVED = 'moved'
    WOULD_MOVE = 'would move (dry run)'
    CONFLICT = 'conflict, both kept'
    CONFLICT_OLD_DROPPED = 'conflict, flat file dropped'
    ERROR = 'error'


class Result(NamedTuple):
    name: str
    outcome: Outcome
    detail: str


@dataclass
class Summary:
    candidates: int = 0
    skipped: list[str] = field(default_factory=list)
    counts: dict[Outcome, int] = field(default_factory=lambda: {o: 0 for o in Outcome})
    errors: list[Result] = field(default_factory=list)
    conflicts: list[Result] = field(default_factory=list)
    seconds: float = 0.0


def list_flat_files(variable_dir: str) -> tuple[list[tuple[str, int]], list[str]]:
    """(name, variableId) of every flat cache file, and the names of every other entry."""
    candidates: list[tuple[str, int]] = []
    skipped: list[str] = []
    with os.scandir(variable_dir) as entries:
        for entry in entries:
            m = FLAT_NAME.match(entry.name)
            if m and entry.is_file(follow_symlinks=False):
                candidates.append((entry.name, int(m.group(1))))
            else:
                skipped.append(entry.name)
    return candidates, skipped


def migrate_one(variable_dir: str, name: str, variable_id: int, dry_run: bool, drop_old_on_conflict: bool) -> Result:
    src = os.path.join(variable_dir, name)
    trg = os.path.join(variable_dir, v2_relative_path(variable_id, name))
    try:
        if os.path.lexists(trg):
            if drop_old_on_conflict and not dry_run:
                os.remove(src)
                return Result(name, Outcome.CONFLICT_OLD_DROPPED, trg)
            return Result(name, Outcome.CONFLICT, trg)
        if dry_run:
            return Result(name, Outcome.WOULD_MOVE, trg)
        os.makedirs(os.path.dirname(trg), exist_ok=True)
        # the target was checked above; on Windows os.rename never overwrites, an existing target is an error
        os.rename(src, trg)
        return Result(name, Outcome.MOVED, trg)
    except OSError as e:
        return Result(name, Outcome.ERROR, f'{type(e).__name__}: {e}')


def batches(items: list, size: int) -> Iterator[list]:
    for i in range(0, len(items), size):
        yield items[i:i + size]


def migrate(variable_dir: str, workers: int = 16, dry_run: bool = False, drop_old_on_conflict: bool = False,
            batch_size: int = DEFAULT_BATCH_SIZE,
            progress: Optional[Callable[[int, int], None]] = None) -> Summary:
    start = time.monotonic()
    candidates, skipped = list_flat_files(variable_dir)
    summary = Summary(candidates=len(candidates), skipped=skipped)

    def migrate_batch(batch: list[tuple[str, int]]) -> list[Result]:
        return [migrate_one(variable_dir, name, variable_id, dry_run, drop_old_on_conflict) for name, variable_id in batch]

    done = 0
    with ThreadPoolExecutor(max_workers=workers) as executor:
        for results in executor.map(migrate_batch, batches(candidates, batch_size)):
            for r in results:
                summary.counts[r.outcome] += 1
                if r.outcome is Outcome.ERROR:
                    summary.errors.append(r)
                elif r.outcome in (Outcome.CONFLICT, Outcome.CONFLICT_OLD_DROPPED):
                    summary.conflicts.append(r)
            done += len(results)
            if progress:
                progress(done, len(candidates))
    summary.seconds = time.monotonic() - start
    return summary


def print_summary(summary: Summary, out=None, max_listed: int = 20) -> None:
    # sys.stdout is resolved at the call, not bound once at import
    out = out if out is not None else sys.stdout
    print(f'flat cache files found: {summary.candidates}', file=out)
    for outcome in Outcome:
        print(f'  {outcome.value}: {summary.counts[outcome]}', file=out)
    print(f'other entries left as they are: {len(summary.skipped)}', file=out)
    print(f'seconds: {summary.seconds:.1f}', file=out)
    for title, results in (('errors', summary.errors), ('conflicts', summary.conflicts)):
        if results:
            print(f'{title} (first {min(max_listed, len(results))} of {len(results)}):', file=out)
            for r in results[:max_listed]:
                print(f'  {r.name}: {r.detail}', file=out)


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description='Moves the Dispatcher variable cache from <variable_dir>/variable-<id> '
                    'to <variable_dir>/v2/<power10>/<power7>/<power4>/variable-<id>. Stop the Dispatcher first.')
    parser.add_argument('variable_dir', nargs='?', default=DEFAULT_VARIABLE_DIR,
                        help=f'${{mh.home}}/dispatcher/temp/variable, default: {DEFAULT_VARIABLE_DIR}')
    parser.add_argument('--dry-run', action='store_true', help='report what would be moved, move nothing')
    parser.add_argument('--workers', type=int, default=16, help='threads doing the moves, default: 16')
    parser.add_argument('--batch-size', type=int, default=DEFAULT_BATCH_SIZE,
                        help=f'files per task of a thread, default: {DEFAULT_BATCH_SIZE}')
    parser.add_argument('--drop-old-on-conflict', action='store_true',
                        help='when the v2 target already exists, delete the flat file (default: keep both)')
    args = parser.parse_args(argv)

    if not os.path.isdir(args.variable_dir):
        print(f'not a dir: {args.variable_dir}', file=sys.stderr)
        return 2
    if args.workers < 1 or args.batch_size < 1:
        print('--workers and --batch-size must be >= 1', file=sys.stderr)
        return 2

    print(f'variable dir: {args.variable_dir}{" (dry run)" if args.dry_run else ""}, workers: {args.workers}', flush=True)
    summary = migrate(args.variable_dir, workers=args.workers, dry_run=args.dry_run,
                      drop_old_on_conflict=args.drop_old_on_conflict, batch_size=args.batch_size,
                      progress=lambda done, total: print(f'processed {done} of {total}', flush=True))
    print_summary(summary)
    return 1 if summary.errors else 0


if __name__ == '__main__':
    sys.exit(main())
