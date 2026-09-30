package me.blueslime.meteor.paper.extras.conditions.operator;

public record ComparisonExpression(
        String left,
        ComparisonOperator operator,
        String right
) {}
