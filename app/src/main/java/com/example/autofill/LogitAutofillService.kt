package com.example.autofill

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.example.R
import com.example.data.local.VaultDatabase
import com.example.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class LogitAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structures = request.fillContexts
        val latestStructure = structures.lastOrNull()?.structure ?: run {
            callback.onSuccess(null)
            return
        }

        val parsed = AutofillUtils.parseStructure(latestStructure)
        val usernameId = parsed.usernameId
        val passwordId = parsed.passwordId
        val focusedId = parsed.focusedId

        // Si no hay username ni password pero hay un campo enfocado, usarlo como objetivo
        val targetId = usernameId ?: passwordId ?: focusedId
        if (targetId == null) {
            callback.onSuccess(null)
            return
        }

        val autofillIds = listOfNotNull(usernameId, passwordId, focusedId).distinct().toTypedArray()

        serviceScope.launch {
            try {
                val cryptoManager = CryptoManager(applicationContext)
                val database = VaultDatabase.getInstance(applicationContext)

                // Crear PendingIntent para la autenticación y selección de credenciales
                val pickerIntent = Intent(this@LogitAutofillService, AutofillPickerActivity::class.java).apply {
                    putExtra("extra_username_id", usernameId ?: targetId)
                    putExtra("extra_password_id", passwordId)
                    putExtra("extra_target_package", parsed.targetPackage)
                    putExtra("extra_target_domain", parsed.targetWebDomain)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_CANCEL_CURRENT
                }

                val pendingIntent = PendingIntent.getActivity(
                    this@LogitAutofillService,
                    1001,
                    pickerIntent,
                    flags
                )

                val responseBuilder = FillResponse.Builder()

                // Si la bóveda está desbloqueada en memoria, buscar credenciales coincidentes
                var datasetsAdded = 0
                if (cryptoManager.isUnlocked) {
                    val entities = try {
                        database.credentialDao().getAllCredentialsList()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val targetDomain = parsed.targetWebDomain?.lowercase() ?: ""
                    val targetPkg = parsed.targetPackage?.lowercase() ?: ""

                    // Priorizar credenciales que coincidan con el dominio o paquete de la app
                    val matchingCredentials = entities.mapNotNull { entity ->
                        try {
                            val title = cryptoManager.decryptString(entity.encryptedTitle)
                            val user = cryptoManager.decryptString(entity.encryptedUsername)
                            val pass = cryptoManager.decryptString(entity.encryptedPassword)
                            val url = cryptoManager.decryptString(entity.encryptedUrl)
                            Triple(entity, Pair(title, user), Pair(pass, url))
                        } catch (e: Exception) {
                            null
                        }
                    }.filter { (_, names, secrets) ->
                        val (title, user) = names
                        val (_, url) = secrets
                        val combined = "$title $user $url".lowercase()
                        targetDomain.isNotEmpty() && combined.contains(targetDomain) ||
                                targetPkg.isNotEmpty() && (combined.contains(targetPkg) || targetPkg.contains(title.lowercase()))
                    }.take(3)

                    for ((_, names, secrets) in matchingCredentials) {
                        val (title, user) = names
                        val (pass, _) = secrets

                        val datasetBuilder = Dataset.Builder()
                        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                            setTextViewText(R.id.autofill_title, title)
                            setTextViewText(R.id.autofill_subtitle, user.ifBlank { "Credencial guardada" })
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            request.inlineSuggestionsRequest != null &&
                            request.inlineSuggestionsRequest!!.inlinePresentationSpecs.isNotEmpty()
                        ) {
                            val spec = request.inlineSuggestionsRequest!!.inlinePresentationSpecs[0]
                            val inline = createInlinePresentation(spec, pendingIntent, title, user)
                            usernameId?.let { datasetBuilder.setValue(it, AutofillValue.forText(user), presentation, inline) }
                            passwordId?.let { datasetBuilder.setValue(it, AutofillValue.forText(pass), presentation, inline) }
                        } else {
                            usernameId?.let { datasetBuilder.setValue(it, AutofillValue.forText(user), presentation) }
                            passwordId?.let { datasetBuilder.setValue(it, AutofillValue.forText(pass), presentation) }
                        }
                        responseBuilder.addDataset(datasetBuilder.build())
                        datasetsAdded++
                    }
                }

                // Presentación desplegable estándar (RemoteViews)
                val dropdownPresentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                    setTextViewText(R.id.autofill_title, "LogitPass")
                    setTextViewText(R.id.autofill_subtitle, "Toca para autorrellenar contraseña")
                }

                // Presentación en la barra del teclado (Inline Suggestions en Android 11+ / API 30+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    request.inlineSuggestionsRequest != null &&
                    request.inlineSuggestionsRequest!!.inlinePresentationSpecs.isNotEmpty()
                ) {
                    val spec = request.inlineSuggestionsRequest!!.inlinePresentationSpecs[0]
                    val inlinePresentation = createInlinePresentation(
                        spec = spec,
                        pendingIntent = pendingIntent,
                        title = "🔑 LogitPass",
                        subtitle = "Autorrellenar"
                    )

                    // setAuthentication con presentación en teclado e inline
                    responseBuilder.setAuthentication(
                        autofillIds,
                        pendingIntent.intentSender,
                        dropdownPresentation,
                        inlinePresentation
                    )
                } else {
                    responseBuilder.setAuthentication(
                        autofillIds,
                        pendingIntent.intentSender,
                        dropdownPresentation
                    )
                }

                callback.onSuccess(responseBuilder.build())
            } catch (e: Exception) {
                e.printStackTrace()
                callback.onSuccess(null)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun createInlinePresentation(
        spec: InlinePresentationSpec,
        pendingIntent: PendingIntent,
        title: String,
        subtitle: String?
    ): InlinePresentation {
        val icon = Icon.createWithResource(this, R.mipmap.ic_launcher)
        val contentBuilder = InlineSuggestionUi.newContentBuilder(pendingIntent)
            .setTitle(title)
            .setStartIcon(icon)

        if (!subtitle.isNullOrBlank()) {
            contentBuilder.setSubtitle(subtitle)
        }

        val slice = contentBuilder.build().slice
        return InlinePresentation(slice, spec, false)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }
}
