package dj.velox.client.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Onboarding — un seul écran (port fidèle de `onboarding_screen.dart`).
 * Logo Velox (velox1.svg via Coil) + titre Poppins + texte Inter + bouton
 * néon « DÉMARRER », sur fond Kinetic Monolith.
 */
@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    val c = VeloxTheme.colors
    val context = LocalContext.current
    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(2f))

        // Illustration (logo Velox)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(14f),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/velox1.svg")
                    .build(),
                imageLoader = svgLoader,
                contentDescription = "Velox",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.weight(2f))

        Text(
            text = stringResource(dj.velox.client.R.string.onboarding_title),
            color = c.onSurface,
            fontFamily = Poppins,
            fontWeight = FontWeight.W700,
            fontSize = 24.sp,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(dj.velox.client.R.string.onboarding_subtitle),
            color = c.onSurfaceVariant,
            fontFamily = Inter,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(3f))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.primary,
                contentColor = c.onPrimary,
            ),
        ) {
            Text(
                text = stringResource(dj.velox.client.R.string.onboarding_start).uppercase(),
                fontFamily = Inter,
                fontWeight = FontWeight.W700,
                fontSize = 15.sp,
                letterSpacing = 1.2.sp,
            )
        }

        Spacer(Modifier.weight(1f))
    }
}
