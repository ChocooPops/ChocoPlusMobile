package com.chocoplusmobile.screens.profile

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chocoplusmobile.api.NetworkModule
import com.chocoplusmobile.state.LocalNavController
import com.chocoplusmobile.ui.components.ErrorState
import com.chocoplusmobile.ui.components.LoadingState
import com.chocoplusmobile.ui.components.ScreenHeaderBack
import com.chocoplusmobile.ui.theme.ChocoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.chocoplusmobile.i18n.Tr
import com.chocoplusmobile.i18n.t
import com.chocoplusmobile.i18n.text

/**
 * Mirrors the RN app's screens/Profile/DocumentationScreen.tsx — but the
 * backend only ever served one thing here (GET documentation, a raw
 * `inline application/pdf` file), not a structured FAQ, so this just
 * downloads and renders that PDF page by page instead of a hardcoded
 * accordion of guide text. Uses Android's own PdfRenderer (no external
 * library needed): the file is written to cache once, then each page is
 * rasterized to a Bitmap sized to the screen's width.
 */
@Composable
fun DocumentationScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val screenWidthPx = remember { context.resources.displayMetrics.widthPixels }

    var pages by remember { mutableStateOf(listOf<Bitmap>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }

    LaunchedEffect(retryTick) {
        loading = true
        error = null
        pages = emptyList()
        try {
            val body = NetworkModule.safeCall { NetworkModule.api.getDocumentationPdf() }
            pages = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "chocoplus_documentation.pdf")
                file.outputStream().use { out -> body.byteStream().copyTo(out) }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        (0 until renderer.pageCount).map { i ->
                            renderer.openPage(i).use { page ->
                                val height = (screenWidthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(screenWidthPx, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(AndroidColor.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            error = e.message ?: Tr.DOC_LOAD_FAILED.text()
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ChocoColors.bg).padding(top = 50.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)) {
            ScreenHeaderBack(title = t(Tr.PROFILE_DOCUMENTATION), onBack = { navController.popBackStack() })
        }
        when {
            loading -> LoadingState(modifier = Modifier.padding(top = 40.dp))
            error != null -> ErrorState(message = error!!, onRetry = { retryTick++ }, modifier = Modifier.padding(top = 40.dp))
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = com.chocoplusmobile.ui.components.TAB_BAR_CLEARANCE),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(pages.size) { i ->
                        Image(
                            bitmap = pages[i].asImageBitmap(),
                            contentDescription = t(Tr.DOC_PAGE_N, i + 1),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }
    }
}
