# Task: Fix SpEL condition evaluation in MH to support simple boolean variable references

## Problem

The `condition` field in SourceCode process definitions is evaluated via SpEL in
`TaskWithInternalContextEventService` (line ~185) using `EvaluateExpressionLanguage.evaluate()`.

Currently, a simple boolean condition like `condition: hasObjectives` does NOT work.
It returns a `VariableHolder` object instead of a `Boolean`, causing error:
```
706.300 condition 'hasObjectives has returned not boolean value but VariableHolder
```

Similarly, `condition: hasObjectives==true` also does NOT work because the `==` operator
goes through `TypeComparator.compare()` which calls `getValueInteger()`, failing with:
```
For input string: "false"
```

The only working pattern today is the ternary workaround:
```yaml
condition: 'hasObjectives ? true : false'
```
This works because the ternary `?` operator triggers SpEL's `TypeConverter.convertValue()`
which handles `VariableHolder → Boolean`. But this is unintuitive — users should be able
to write `condition: hasObjectives` or `condition: hasObjectives==true`.

This was never caught because conditions were previously only used in the factorial/tail
recursion demo (`test-condition-related-1.0.yaml`), always with the ternary pattern.
Now RG uses conditions in production (`mh.nop` with `condition: hasObjectives`).

## Root cause analysis

Three problems in `EvaluateExpressionLanguage.MhEvalContext`:

### 1. `TypeConverter.canConvert()` always returns `false`

```java
public boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
    return false;  // <-- SpEL never auto-converts anything
}
```

SpEL calls `canConvert()` before `convertValue()`. Since it returns `false`, SpEL never
attempts type conversion for simple property references or equality comparisons. This is
why a bare `hasObjectives` returns the raw `VariableHolder` — SpEL sees `canConvert=false`
and doesn't try to convert it to Boolean.

**Fix**: `canConvert()` should return `true` when the conversion is actually supported,
i.e. when source is `VariableHolder` and target is `Boolean` or `Integer`.

### 2. `TypeComparator.compare()` only handles Integer

```java
public int compare(Object firstObject, Object secondObject) {
    Integer firstValue = getValueInteger(firstObject);  // fails on "false" string
    Integer secondValue = getValueInteger(secondObject);
    return firstValue.compareTo(secondValue);
}
```

When evaluating `hasObjectives==true`, SpEL uses `TypeComparator.compare()` which calls
`getValueInteger()` on both operands. The left operand is a `VariableHolder` containing
string `"false"`, and `Integer.valueOf("false")` throws `NumberFormatException`.

**Fix**: `compare()` should detect the types of operands and use appropriate comparison.
When comparing a `VariableHolder` against a `Boolean` literal, it should read the variable
as boolean and compare booleans. The `canCompare()` method should also be updated to
reflect what types are actually supported.

### 3. No handling of `VariableHolder` result at the call site

In `TaskWithInternalContextEventService` (line ~190):
```java
Object obj = EvaluateExpressionLanguage.evaluate(...);
if (obj!=null && !(obj instanceof Boolean)) {
    // throws 706.300
}
```

Even if the SpEL evaluator is fixed, a defensive fallback here would be good: if the
result is a `VariableHolder`, extract the boolean value from the variable content rather
than throwing an error.

## Files to modify

1. **`EvaluateExpressionLanguage.java`** — `MhEvalContext` inner class:
   - `TypeConverter.canConvert()` — return `true` for supported conversions
     (`VariableHolder → Boolean`, `VariableHolder → Integer`)
   - `TypeComparator.compare()` — handle Boolean comparisons, not just Integer
   - `TypeComparator.canCompare()` — support Boolean operands

2. **`TaskWithInternalContextEventService.java`** — condition evaluation block (~line 190):
   - Add defensive `VariableHolder → Boolean` conversion after `evaluate()` returns,
     before the `instanceof Boolean` check

## Expected behavior after fix

All three forms should work:
- `condition: hasObjectives` — SpEL returns VariableHolder, call site converts to Boolean
- `condition: hasObjectives==true` — SpEL compares VariableHolder with Boolean via TypeComparator
- `condition: 'hasObjectives ? true : false'` — continues to work as before (ternary)

## Test plan

The existing test `test-condition-related-1.0.yaml` uses only the ternary pattern.
Add new test cases that verify:
1. `condition: varName` (bare variable reference) — works for both `"true"` and `"false"` values
2. `condition: varName==true` — equality comparison with boolean literal
3. `condition: varName==false` — equality comparison with false
4. `condition: '!varName ? true : false'` — negation (already tested, keep for regression)

## Current workaround

All production SourceCode YAMLs in RG now use the ternary pattern:
```yaml
condition: 'hasObjectives ? true : false'
```
This is functional but unintuitive and should not be the required pattern long-term.
