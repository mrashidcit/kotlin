// "class org.jetbrains.jet.plugin.intentions.ConvertToExpressionBodyAction" "false"

fun foo(handler: () -> Unit) { }

fun bar() {
    foo { <caret>zoo() }
}

fun zoo(){}