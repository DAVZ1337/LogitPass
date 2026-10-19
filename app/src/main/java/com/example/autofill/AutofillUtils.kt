package com.example.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.autofill.AutofillId

data class ParsedAutofillStructure(
    val usernameId: AutofillId? = null,
    val passwordId: AutofillId? = null,
    val focusedId: AutofillId? = null,
    val targetPackage: String? = null,
    val targetWebDomain: String? = null
)

object AutofillUtils {

    fun parseStructure(structure: AssistStructure): ParsedAutofillStructure {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var focusedId: AutofillId? = null
        var targetPackage: String? = null
        var targetWebDomain: String? = null

        val nodeCount = structure.windowNodeCount
        for (i in 0 until nodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootNode = windowNode.rootViewNode
            if (targetPackage == null && rootNode.idPackage != null) {
                targetPackage = rootNode.idPackage
            }

            val result = traverseNode(rootNode)
            if (result.usernameId != null && usernameId == null) usernameId = result.usernameId
            if (result.passwordId != null && passwordId == null) passwordId = result.passwordId
            if (result.focusedId != null && focusedId == null) focusedId = result.focusedId
            if (result.targetWebDomain != null && targetWebDomain == null) targetWebDomain = result.targetWebDomain
            if (result.targetPackage != null && targetPackage == null) targetPackage = result.targetPackage
        }

        return ParsedAutofillStructure(
            usernameId = usernameId,
            passwordId = passwordId,
            focusedId = focusedId,
            targetPackage = targetPackage,
            targetWebDomain = targetWebDomain
        )
    }

    private fun traverseNode(node: AssistStructure.ViewNode): ParsedAutofillStructure {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var focusedId: AutofillId? = if (node.isFocused) node.autofillId else null
        var targetWebDomain: String? = node.webDomain
        var targetPkg: String? = node.idPackage

        val hints = node.autofillHints
        val idEntry = node.idEntry?.lowercase() ?: ""
        val hintText = node.hint?.lowercase() ?: ""
        val inputType = node.inputType

        // Descartar campos que sean barras de búsqueda, notas, comentarios o chats
        val isNonAuthField = idEntry.contains("search") || hintText.contains("search") ||
                hintText.contains("buscar") || idEntry.contains("buscar") ||
                idEntry.contains("query") || idEntry.contains("filter") ||
                idEntry.contains("comment") || idEntry.contains("mensaje") ||
                idEntry.contains("chat") || idEntry.contains("tweet") ||
                idEntry.contains("compose") || idEntry.contains("note")

        if (!isNonAuthField) {
            // 1. Chequeo por autofillHints del sistema
            if (hints != null) {
                for (hint in hints) {
                    val h = hint.lowercase()
                    when {
                        h.contains("username") || h.contains("email") || h.contains("user") || h.contains("account") -> {
                            usernameId = node.autofillId
                        }
                        h.contains("password") || h.contains("pass") || h.contains("pin") -> {
                            passwordId = node.autofillId
                        }
                    }
                }
            }

            // 2. Chequeo por InputType de Android
            if (passwordId == null && inputType != 0) {
                val variation = inputType and InputType.TYPE_MASK_VARIATION
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                ) {
                    passwordId = node.autofillId
                }
            }

            if (usernameId == null && inputType != 0) {
                val variation = inputType and InputType.TYPE_MASK_VARIATION
                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                ) {
                    usernameId = node.autofillId
                }
            }

            // 3. Chequeo por id del elemento o texto del hint en español/inglés
            if (usernameId == null) {
                if (idEntry.contains("username") || idEntry.contains("email") || idEntry.contains("login_id") ||
                    idEntry.contains("login_user") || idEntry.contains("usuario") || idEntry.contains("correo") ||
                    idEntry.contains("cuenta") ||
                    hintText.contains("correo") || hintText.contains("email") || hintText.contains("usuario") ||
                    hintText.contains("user name") || hintText.contains("nombre de usuario")
                ) {
                    usernameId = node.autofillId
                }
            }

            if (passwordId == null) {
                if (idEntry.contains("password") || idEntry.contains("passwd") || idEntry.contains("pwd") ||
                    idEntry.contains("contrasena") || idEntry.contains("contraseña") || idEntry.contains("clave") ||
                    idEntry.contains("passcode") || idEntry.contains("pin") ||
                    hintText.contains("contraseña") || hintText.contains("contrasena") ||
                    hintText.contains("password") || hintText.contains("clave") || hintText.contains("código pin")
                ) {
                    passwordId = node.autofillId
                }
            }
        }

        // 4. Recorrido recursivo de hijos
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i)
            val childResult = traverseNode(child)
            if (childResult.usernameId != null && usernameId == null) usernameId = childResult.usernameId
            if (childResult.passwordId != null && passwordId == null) passwordId = childResult.passwordId
            if (childResult.focusedId != null && focusedId == null) focusedId = childResult.focusedId
            if (childResult.targetWebDomain != null && targetWebDomain == null) targetWebDomain = childResult.targetWebDomain
            if (childResult.targetPackage != null && targetPkg == null) targetPkg = childResult.targetPackage
        }

        return ParsedAutofillStructure(
            usernameId = usernameId,
            passwordId = passwordId,
            focusedId = focusedId,
            targetPackage = targetPkg,
            targetWebDomain = targetWebDomain
        )
    }
}
