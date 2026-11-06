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
Tests of migrate_variable_cache_v2 on a real filesystem (pytest tmp_path), no doubles.
Expected v2 paths are written out literally, the same values as DigitUtilsTest / AssetUtilsVariableCacheDirTest.
"""

import os

import pytest

import migrate_variable_cache_v2 as m


def flat(variable_dir, variable_id):
    return variable_dir / f'variable-{variable_id}'


def v2(variable_dir, power10, power7, power4, variable_id):
    return variable_dir / 'v2' / power10 / power7 / power4 / f'variable-{variable_id}'


def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    return path


@pytest.fixture
def variable_dir(tmp_path):
    d = tmp_path / 'dispatcher' / 'temp' / 'variable'
    d.mkdir(parents=True)
    return d


@pytest.mark.parametrize('num, expected', [
    (0, (0, 0, 0)),
    (42, (0, 0, 42)),
    (9_999, (0, 0, 9999)),
    (10_000, (0, 1, 0)),
    (112_222, (0, 11, 2222)),
    (9_999_999, (0, 999, 9999)),
    (10_000_000, (1, 0, 0)),
    (123_456_789, (12, 345, 6789)),
    (9_876_543_210, (987, 654, 3210)),
    (9_223_372_036_854_775_807, (922_337_203_685, 477, 5807)),
])
def test_get_power3_is_the_split_of_java_getPower3(num, expected):
    assert m.get_power3(num) == expected


def test_v2_relative_path_of_the_task_example():
    assert m.v2_relative_path(112_222, 'variable-112222') == os.path.join('v2', '0', '11', '2222', 'variable-112222')


def test_migrate_moves_flat_files_to_v2(variable_dir):
    ids = {42: ('0', '0', '42'), 112_222: ('0', '11', '2222'), 123_456_789: ('12', '345', '6789'),
           10_000_000: ('1', '0', '0')}
    for variable_id in ids:
        write(flat(variable_dir, variable_id), f'content-{variable_id}')

    summary = m.migrate(str(variable_dir), workers=4)

    for variable_id, (p10, p7, p4) in ids.items():
        target = v2(variable_dir, p10, p7, p4, variable_id)
        assert target.read_text(encoding='utf-8') == f'content-{variable_id}', f'#{variable_id} at {target}'
        assert not flat(variable_dir, variable_id).exists(), f'#{variable_id}: the flat file must be gone'
    assert summary.candidates == 4
    assert summary.counts[m.Outcome.MOVED] == 4
    assert summary.errors == []


def test_migrate_leaves_other_entries_as_they_are(variable_dir):
    others = ['global_variable-5', 'variable-abc', 'var-1-123.bin', 'variable-12.bin', 'variable-']
    for name in others:
        write(variable_dir / name, name)
    (variable_dir / 'variable-77').mkdir()  # a dir with a cache-like name is not a cache file
    write(flat(variable_dir, 7), 'seven')

    summary = m.migrate(str(variable_dir), workers=2)

    for name in others:
        assert (variable_dir / name).read_text(encoding='utf-8') == name, f'{name} must be left in place'
    assert (variable_dir / 'variable-77').is_dir()
    assert v2(variable_dir, '0', '0', '7', 7).read_text(encoding='utf-8') == 'seven'
    assert summary.candidates == 1
    assert sorted(summary.skipped) == sorted(others + ['variable-77'])


def test_migrate_is_idempotent(variable_dir):
    write(flat(variable_dir, 112_222), 'x')
    first = m.migrate(str(variable_dir), workers=2)
    second = m.migrate(str(variable_dir), workers=2)

    assert first.counts[m.Outcome.MOVED] == 1
    assert second.candidates == 0
    assert second.counts[m.Outcome.MOVED] == 0
    assert second.skipped == ['v2']
    assert v2(variable_dir, '0', '11', '2222', 112_222).read_text(encoding='utf-8') == 'x'


def test_dry_run_moves_nothing(variable_dir):
    src = write(flat(variable_dir, 112_222), 'x')

    summary = m.migrate(str(variable_dir), workers=2, dry_run=True)

    assert src.read_text(encoding='utf-8') == 'x'
    assert not (variable_dir / 'v2').exists(), 'a dry run must not create the v2 dir'
    assert summary.counts[m.Outcome.WOULD_MOVE] == 1
    assert summary.counts[m.Outcome.MOVED] == 0


def test_conflict_keeps_both_by_default(variable_dir):
    old = write(flat(variable_dir, 112_222), 'OLD')
    new = write(v2(variable_dir, '0', '11', '2222', 112_222), 'NEW')

    summary = m.migrate(str(variable_dir), workers=2)

    assert new.read_text(encoding='utf-8') == 'NEW', 'an existing v2 file must never be overwritten'
    assert old.read_text(encoding='utf-8') == 'OLD'
    assert summary.counts[m.Outcome.CONFLICT] == 1
    assert [r.name for r in summary.conflicts] == ['variable-112222']


def test_conflict_with_drop_old_deletes_the_flat_file(variable_dir):
    old = write(flat(variable_dir, 112_222), 'OLD')
    new = write(v2(variable_dir, '0', '11', '2222', 112_222), 'NEW')

    summary = m.migrate(str(variable_dir), workers=2, drop_old_on_conflict=True)

    assert new.read_text(encoding='utf-8') == 'NEW'
    assert not old.exists()
    assert summary.counts[m.Outcome.CONFLICT_OLD_DROPPED] == 1


def test_dry_run_with_drop_old_deletes_nothing(variable_dir):
    old = write(flat(variable_dir, 112_222), 'OLD')
    write(v2(variable_dir, '0', '11', '2222', 112_222), 'NEW')

    summary = m.migrate(str(variable_dir), workers=2, dry_run=True, drop_old_on_conflict=True)

    assert old.read_text(encoding='utf-8') == 'OLD'
    assert summary.counts[m.Outcome.CONFLICT] == 1


def test_many_files_in_parallel_batches(variable_dir):
    # 3 000 ids around the 10^4 and 10^7 boundaries, small batches so many threads share middle dirs
    ids = list(range(8_500, 11_500)) + list(range(9_999_000, 10_001_000))
    for variable_id in ids:
        write(flat(variable_dir, variable_id), str(variable_id))

    summary = m.migrate(str(variable_dir), workers=8, batch_size=37)

    assert summary.candidates == len(ids)
    assert summary.counts[m.Outcome.MOVED] == len(ids)
    assert summary.errors == []
    for variable_id in ids:
        p10, p7, p4 = (str(x) for x in m.get_power3(variable_id))
        target = variable_dir / 'v2' / p10 / p7 / p4 / f'variable-{variable_id}'
        assert target.read_text(encoding='utf-8') == str(variable_id), f'#{variable_id}'
    assert os.listdir(variable_dir) == ['v2'], 'nothing flat may remain'


def test_progress_reaches_the_total(variable_dir):
    for variable_id in range(1, 11):
        write(flat(variable_dir, variable_id), 'x')
    seen = []

    m.migrate(str(variable_dir), workers=2, batch_size=3, progress=lambda done, total: seen.append((done, total)))

    assert seen[-1] == (10, 10)
    assert [done for done, _ in seen] == sorted(done for done, _ in seen)


def test_main_returns_2_for_a_missing_dir(tmp_path, capsys):
    assert m.main([str(tmp_path / 'absent')]) == 2
    assert 'not a dir' in capsys.readouterr().err


def test_main_returns_2_for_zero_workers(variable_dir):
    assert m.main([str(variable_dir), '--workers', '0']) == 2


def test_main_moves_and_returns_0(variable_dir, capsys):
    write(flat(variable_dir, 112_222), 'x')

    assert m.main([str(variable_dir), '--workers', '2']) == 0

    assert v2(variable_dir, '0', '11', '2222', 112_222).read_text(encoding='utf-8') == 'x'
    out = capsys.readouterr().out
    assert 'flat cache files found: 1' in out
    assert 'moved: 1' in out
