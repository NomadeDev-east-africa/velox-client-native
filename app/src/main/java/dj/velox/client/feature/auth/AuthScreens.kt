package dj.velox.client.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxTheme
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════
// Couleurs spécifiques à l'auth (fidèles à la maquette Flutter,
// volontairement différentes du néon « Kinetic Monolith » du reste de l'app).
// ════════════════════════════════════════════════════════════════
private val AuthGreen = Color(0xFF22A82E)      // bouton vert plein (Sign In)
private val VeloxGreen = Color(0xFF22C55E)     // vert plus vif (logo + chooser Sign Up)
private val PhoneBlue = Color(0xFF5B9BD5)
private val FacebookBlue = Color(0xFF3B5998)
private val GoogleBlue = Color(0xFF4285F4)

// ════════════════════════════════════════════════════════════════
// CONNEXION
// ════════════════════════════════════════════════════════════════

@Composable
fun SignInScreen(
    viewModel: AuthViewModel,
    onNavigateToSignUp: () -> Unit,
    onNavigateToPhone: () -> Unit,
    onNavigateToForgot: () -> Unit,
) {
    val c = VeloxTheme.colors
    val form by viewModel.form.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val fbSoon = stringResource(R.string.auth_facebook_soon)

    AuthScaffold(
        topBarTitle = stringResource(R.string.auth_signin),
        heading = stringResource(R.string.auth_welcome_to),
        subtitle = stringResource(R.string.auth_signin_subtitle),
    ) {
        AuthTextField(email, { email = it }, stringResource(R.string.email_address), KeyboardType.Email)
        Spacer(Modifier.height(14.dp))
        AuthTextField(password, { password = it }, stringResource(R.string.password), isPassword = true)

        form.error?.let { ErrorText(it) }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.auth_forgot_password),
            color = c.onSurface,
            fontFamily = Inter,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(8.dp))
                .padding(4.dp),
        )
        Spacer(Modifier.height(16.dp))

        PrimaryGreenButton(
            text = stringResource(R.string.auth_signin_action),
            loading = form.isSubmitting,
            enabled = email.isNotBlank() && password.isNotBlank(),
            onClick = { viewModel.signIn(email.trim(), password) },
        )

        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.auth_or), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))

        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.auth_no_account_q), color = c.onSurface, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.W600)
            Text(
                stringResource(R.string.auth_create_account_link),
                color = AuthGreen,
                fontFamily = Inter,
                fontSize = 14.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onNavigateToSignUp).padding(2.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        SocialButton(
            text = stringResource(R.string.auth_signin_with_phone),
            container = PhoneBlue,
            onClick = onNavigateToPhone,
        ) { Icon(Icons.Filled.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(12.dp))

        SocialButton(
            text = stringResource(R.string.auth_facebook),
            container = FacebookBlue,
            onClick = {
                android.widget.Toast.makeText(context, fbSoon, android.widget.Toast.LENGTH_SHORT).show()
            },
        ) { BrandBadge("f", FacebookBlue) }
        Spacer(Modifier.height(12.dp))

        GoogleConnectButton(onIdToken = viewModel::signInWithGoogleIdToken)
    }
}

// ════════════════════════════════════════════════════════════════
// INSCRIPTION — écran de choix de méthode (port de signup_page.dart)
// ════════════════════════════════════════════════════════════════

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onNavigateToSignIn: () -> Unit,
    onNavigateToPhone: () -> Unit,
    onNavigateToEmailSignUp: () -> Unit,
) {
    val c = VeloxTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))

        // Logo (icône scooter dans un carré arrondi teinté vert)
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(VeloxGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.DeliveryDining, null, tint = VeloxGreen, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Velox", color = VeloxGreen, fontFamily = Poppins, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.auth_brand_subtitle), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 15.sp)

        Spacer(Modifier.height(44.dp))

        // Titre de section (aligné à gauche)
        Box(Modifier.fillMaxWidth()) {
            Column {
                Text(stringResource(R.string.auth_create_your_account), color = c.onSurface, fontFamily = Poppins, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.auth_join_thousands), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(24.dp))

        // Continuer avec le téléphone (vert plein)
        ChooserButton(
            text = stringResource(R.string.auth_phone_button),
            container = VeloxGreen,
            contentColor = Color.White,
            borderColor = null,
            onClick = onNavigateToPhone,
            leading = { Icon(Icons.Filled.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(22.dp)) },
        )
        Spacer(Modifier.height(14.dp))

        // Continuer avec Google (contour)
        GoogleChooserButton(onIdToken = viewModel::signInWithGoogleIdToken)

        Spacer(Modifier.height(22.dp))
        DividerOu()
        Spacer(Modifier.height(22.dp))

        // S'inscrire avec email (lien souligné + icône)
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onNavigateToEmailSignUp).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.MailOutline, null, tint = c.onSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.auth_signup_email_pwd),
                color = c.onSurface,
                fontFamily = Inter,
                fontSize = 14.sp,
                fontWeight = FontWeight.W600,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.auth_already_account_q), color = c.onSurface, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.W600)
            Text(
                stringResource(R.string.auth_signin_action),
                color = VeloxGreen,
                fontFamily = Inter,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onNavigateToSignIn).padding(2.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.auth_terms_agree),
            color = c.onSurfaceVariant.copy(alpha = 0.7f),
            fontFamily = Inter,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
    }
}

// ════════════════════════════════════════════════════════════════
// INSCRIPTION PAR EMAIL — formulaire (atteint via le lien du chooser)
// ════════════════════════════════════════════════════════════════

@Composable
fun EmailSignUpScreen(
    viewModel: AuthViewModel,
    onNavigateToSignIn: () -> Unit,
) {
    val c = VeloxTheme.colors
    val form by viewModel.form.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthScaffold(
        topBarTitle = stringResource(R.string.auth_signup),
        heading = stringResource(R.string.auth_signup_action),
        subtitle = stringResource(R.string.auth_signup_subtitle),
    ) {
        AuthTextField(name, { name = it }, stringResource(R.string.auth_name))
        Spacer(Modifier.height(14.dp))
        AuthTextField(email, { email = it }, stringResource(R.string.email_address), KeyboardType.Email)
        Spacer(Modifier.height(14.dp))
        AuthTextField(phone, { phone = it }, stringResource(R.string.auth_phone), KeyboardType.Phone)
        Spacer(Modifier.height(14.dp))
        AuthTextField(password, { password = it }, stringResource(R.string.password), isPassword = true)

        form.error?.let { ErrorText(it) }

        Spacer(Modifier.height(20.dp))
        PrimaryGreenButton(
            text = stringResource(R.string.signup),
            loading = form.isSubmitting,
            enabled = name.isNotBlank() && email.isNotBlank() && password.length >= 6,
            onClick = { viewModel.signUp(name.trim(), email.trim(), password, phone.trim().ifEmpty { null }) },
        )

        Spacer(Modifier.height(18.dp))
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.auth_already_account_q), color = c.onSurface, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.W600)
            Text(
                stringResource(R.string.auth_signin_action),
                color = VeloxGreen,
                fontFamily = Inter,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onNavigateToSignIn).padding(2.dp),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
// COMPOSANTS PARTAGÉS (réutilisés par Forgot / Phone / OTP)
// ════════════════════════════════════════════════════════════════

/**
 * Coque d'auth fidèle à la maquette : barre supérieure (titre), gros titre + sous-titre,
 * puis le contenu. [topBarTitle] permet d'afficher un libellé de barre distinct du
 * [heading] (ex. « Sign In » / « Welcome to »). Pour Forgot/Phone/OTP, un seul titre suffit.
 */
@Composable
internal fun AuthScaffold(
    title: String = "",
    subtitle: String? = null,
    heading: String = title,
    topBarTitle: String = title,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = VeloxTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        // Barre supérieure
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Text(topBarTitle, color = c.onSurface, fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = 0.3f)))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Text(heading, color = c.onSurface, fontFamily = Poppins, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, color = c.onSurface.copy(alpha = 0.85f), fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(28.dp))
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    val c = VeloxTheme.colors
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 16.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = c.onSurfaceVariant,
                    )
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = AuthGreen,
            unfocusedBorderColor = c.outlineVariant,
            focusedTextColor = c.onSurface,
            unfocusedTextColor = c.onSurface,
            cursorColor = AuthGreen,
        ),
        modifier = Modifier.fillMaxWidth().height(58.dp),
    )
}

/** Bouton d'action principal vert plein (texte blanc), comme la maquette. */
@Composable
internal fun PrimaryGreenButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = VeloxTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthGreen,
            contentColor = Color.White,
            disabledContainerColor = c.surfaceTop,
            disabledContentColor = c.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.height(22.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** Conservé pour compat : alias vert de l'ancien SubmitButton (Forgot/Phone/OTP). */
@Composable
internal fun SubmitButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) =
    PrimaryGreenButton(text, loading, enabled, onClick)

@Composable
private fun SocialButton(
    text: String,
    container: Color,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
        leading()
        Spacer(Modifier.size(12.dp))
        Text(text, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Pastille blanche avec l'initiale de marque (Facebook « f », Google « G »). */
@Composable
private fun BrandBadge(letter: String, color: Color) {
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = color, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun GoogleConnectButton(onIdToken: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webClientId = stringResource(R.string.default_web_client_id)
    SocialButton(
        text = stringResource(R.string.auth_google),
        container = GoogleBlue,
        onClick = {
            scope.launch {
                runCatching { GoogleSignInHelper.getIdToken(context, webClientId) }
                    .onSuccess(onIdToken)
                    .onFailure { /* annulation : silencieux */ }
            }
        },
    ) { BrandBadge("G", GoogleBlue) }
}

/** Bouton pleine largeur du chooser : icône à gauche, libellé, chevron à droite. */
@Composable
private fun ChooserButton(
    text: String,
    container: Color,
    contentColor: Color,
    borderColor: Color?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.size(12.dp))
        Text(text, color = contentColor, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = contentColor.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun GoogleChooserButton(onIdToken: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webClientId = stringResource(R.string.default_web_client_id)
    val c = VeloxTheme.colors
    ChooserButton(
        text = stringResource(R.string.auth_google),
        container = Color.Transparent,
        contentColor = c.onSurface,
        borderColor = c.outlineVariant,
        onClick = {
            scope.launch {
                runCatching { GoogleSignInHelper.getIdToken(context, webClientId) }
                    .onSuccess(onIdToken)
                    .onFailure { /* annulation : silencieux */ }
            }
        },
        leading = { Text("G", color = GoogleBlue, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
    )
}

@Composable
private fun DividerOu() {
    val c = VeloxTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = c.outlineVariant.copy(alpha = 0.5f))
        Text(stringResource(R.string.auth_or), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
        HorizontalDivider(Modifier.weight(1f), color = c.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
internal fun ErrorText(message: String) {
    val c = VeloxTheme.colors
    Spacer(Modifier.height(12.dp))
    Box(
        Modifier.fillMaxWidth().background(c.error.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(12.dp),
    ) {
        Text(message, color = c.error, fontFamily = Inter, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
