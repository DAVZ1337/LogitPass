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
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.example.R
import com.example.data.VaultRepository
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

        // REGLA CRÍTICA SOLICITADA POR EL USUARIO:
        // El autocompletar SOLO debe aparecer en paneles de inicio de sesión, contraseñas
        // o paneles donde se piden credenciales (usuario/correo o contraseña).
        // Si no se detectó ningún campo de contraseña ni de usuario, no activar autofill.
        if (usernameId == null && passwordId == null) {
            callback.onSuccess(null)
            return
        }

        val autofillIds = listOfNotNull(usernameId, passwordId).toTypedArray()

        serviceScope.launch {
            try {
                val cryptoManager = CryptoManager(applicationContext)
                val database = VaultDatabase.getInstance(applicationContext)
                val repository = VaultRepository(database.credentialDao(), cryptoManager)

                // Intent para abrir el selector completo si se necesita elegir o desbloquear
                val pickerIntent = Intent(this@LogitAutofillService, AutofillPickerActivity::class.java).apply {
                    putExtra("extra_username_id", usernameId)
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
                var datasetsAdded = 0

                // Si la bóveda está desbloqueada en memoria, buscar credenciales
                if (cryptoManager.isUnlocked) {
                    val allCredentials = repository.getAllDecryptedCredentialsList()

                    val targetDomain = parsed.targetWebDomain?.lowercase() ?: ""
                    val targetPkg = parsed.targetPackage?.lowercase() ?: ""

                    // Filtrar credenciales que coincidan con la app o el dominio web
                    val matching = allCredentials.filter { item ->
                        val combined = "${item.title} ${item.username} ${item.url}".lowercase()
                        (targetDomain.isNotEmpty() && combined.contains(targetDomain)) ||
                                (targetPkg.isNotEmpty() && (combined.contains(targetPkg) || targetPkg.contains(item.title.lowercase())))
                    }

                    // Si no hay coincidencias exactas pero hay credenciales, tomar hasta 2 más recientes
                    val toSuggest = if (matching.isNotEmpty()) {
                        matching.take(3)
                    } else {
                        allCredentials.take(2)
                    }

                    for (item in toSuggest) {
                        val datasetBuilder = Dataset.Builder()
                        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                            setTextViewText(R.id.autofill_title, item.title)
                            setTextViewText(R.id.autofill_subtitle, item.username.ifBlank { "Credencial guardada" })
                        }

                        var itemAdded = false

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            request.inlineSuggestionsRequest != null &&
                            request.inlineSuggestionsRequest!!.inlinePresentationSpecs.isNotEmpty()
                        ) {
                            val spec = request.inlineSuggestionsRequest!!.inlinePresentationSpecs[0]
                            val label = if (item.username.isNotBlank()) item.username else item.title
                            val inline = createInlinePresentation(spec, pendingIntent, "🔑 $label", item.title)

                            usernameId?.let { id ->
                                val u = item.username.ifBlank { item.title }
                                datasetBuilder.setValue(id, AutofillValue.forText(u), presentation, inline)
                                itemAdded = true
                            }
                            passwordId?.let { id ->
                                if (item.password.isNotEmpty()) {
                                    datasetBuilder.setValue(id, AutofillValue.forText(item.password), presentation, inline)
                                    itemAdded = true
                                }
                            }
                        } else {
                            usernameId?.let { id ->
                                val u = item.username.ifBlank { item.title }
                                datasetBuilder.setValue(id, AutofillValue.forText(u), presentation)
                                itemAdded = true
                            }
                            passwordId?.let { id ->
                                if (item.password.isNotEmpty()) {
                                    datasetBuilder.setValue(id, AutofillValue.forText(item.password), presentation)
                                    itemAdded = true
                                }
                            }
                        }

                        if (itemAdded) {
                            responseBuilder.addDataset(datasetBuilder.build())
                            datasetsAdded++
                        }
                    }

                    if (datasetsAdded > 0) {
                        // Añadir sugerencia para abrir el selector completo si hay más cuentas
                        val morePresentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                            setTextViewText(R.id.autofill_title, "Otras cuentas en LogitPass...")
                            setTextViewText(R.id.autofill_subtitle, "Toca para ver la lista completa")
                        }

                        val moreDatasetBuilder = Dataset.Builder(morePresentation)
                            .setAuthentication(pendingIntent.intentSender)

                        usernameId?.let { moreDatasetBuilder.setValue(it, null, morePresentation) }
                        passwordId?.let { moreDatasetBuilder.setValue(it, null, morePresentation) }

                        responseBuilder.addDataset(moreDatasetBuilder.build())
                        callback.onSuccess(responseBuilder.build())
                        return@launch
                    }
                }

                // Si la bóveda está bloqueada o no se encontraron sugerencias directas:
                // Mostrar autenticación de LogitPass en teclado y menú desplegable
                val dropdownPresentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                    setTextViewText(R.id.autofill_title, "LogitPass")
                    setTextViewText(R.id.autofill_subtitle, "Toca para autorrellenar contraseña")
                }

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
