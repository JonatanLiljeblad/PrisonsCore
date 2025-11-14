package me.panda19.prisonscore.utils

import net.objecthunter.exp4j.ExpressionBuilder

object FormulaEvaluator {

    /**
     * Evaluate a math expression with named variables.
     * Returns 0.0 on failure and prints stacktrace (safe for live servers).
     */
    fun eval(expression: String, variables: Map<String, Double>): Double {
        return try {
            val builder = ExpressionBuilder(expression)
            // register variables
            variables.keys.forEach { builder.variable(it) }
            val exp = builder.build()
            variables.forEach { (k, v) -> exp.setVariable(k, v) }
            exp.evaluate()
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
    }
}