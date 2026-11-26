package com.sanelink.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sanelink.app.R
import com.sanelink.app.core.sanitizer.DomainSwapper
import com.sanelink.app.ui.theme.GoogleSansCode
import com.sanelink.app.ui.theme.LocalMotionScheme

/**
 * Well-known alternative frontends, offered as one-tap suggestions. Only used to prefill the
 * "Open on" field; users can type any domain.
 */
val KnownFrontends: Map<String, List<String>> = mapOf(
    "instagram.com" to listOf("ddinstagram.com"),
    "reddit.com" to listOf("rxddit.com", "redlib.catsarch.com"),
    "twitter.com" to listOf("fixupx.com", "fxtwitter.com", "vxtwitter.com"),
    "x.com" to listOf("fixupx.com", "fxtwitter.com", "vxtwitter.com"),
    "tiktok.com" to listOf("vxtiktok.com"),
    "youtube.com" to listOf("yewtu.be"),
)

/** Result of validating the two domain fields. */
data class DomainSwapInput(val from: String?, val to: String?) {
    /** Both fields empty/unused: no swap. */
    val isEmpty: Boolean get() = to == null
    val isValid: Boolean get() = from != null && to != null && from != to
}

fun parseDomainSwapInput(fromText: String, toText: String): DomainSwapInput = DomainSwapInput(
    from = DomainSwapper.normalizeDomain(fromText),
    to = if (toText.isBlank()) null else DomainSwapper.normalizeDomain(toText)
)

/**
 * "Open links on a different site" editor: two plain domain fields, no regex.
 *
 * @param fromEditable false when the source domain comes from elsewhere (a custom rule's
 *   website field); it is then shown read-only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DomainSwapEditor(
    fromText: String,
    onFromChange: (String) -> Unit,
    toText: String,
    onToChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fromEditable: Boolean = true
) {
    val motionScheme = LocalMotionScheme.current
    val parsed = parseDomainSwapInput(fromText, toText)
    val fromError = fromText.isNotBlank() && parsed.from == null
    val toError = toText.isNotBlank() && (parsed.to == null || parsed.to == parsed.from)
    val suggestions = parsed.from?.let { KnownFrontends[it] }.orEmpty().filter { it != parsed.to }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DomainField(
                value = fromText,
                onValueChange = onFromChange,
                label = "Links on",
                placeholder = "instagram.com",
                isError = fromError,
                errorText = "Enter a website like instagram.com",
                readOnly = !fromEditable
            )

            // Connector badge between the fields
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val active = parsed.isValid
                val badgeColor by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    motionScheme.defaultColorEffects,
                    label = "swapBadgeColor"
                )
                val iconColor by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    motionScheme.fastColorEffects,
                    label = "swapBadgeIcon"
                )
                val rotation by animateFloatAsState(if (active) 90f else 0f, motionScheme.defaultSpatial, label = "swapBadgeRotation")
                Box(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_swap_horiz),
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
            }

            DomainField(
                value = toText,
                onValueChange = onToChange,
                label = "Open on",
                placeholder = suggestions.firstOrNull() ?: "oginstagram.com",
                isError = toError,
                errorText = if (parsed.to != null && parsed.to == parsed.from) {
                    "Pick a different website"
                } else {
                    "Enter a website like oginstagram.com"
                },
                imeAction = ImeAction.Done
            )

            AnimatedVisibility(
                visible = suggestions.isNotEmpty(),
                enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.fastEffects),
                exit = shrinkVertically(motionScheme.defaultSpatialSpec()) + fadeOut(motionScheme.fastEffects)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    suggestions.forEach { domain ->
                        SuggestionChip(text = domain, onClick = { onToChange(domain) })
                    }
                }
            }

            // Plain-language preview: example.com/path → new.com/path
            AnimatedVisibility(
                visible = parsed.isValid,
                enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.fastEffects),
                exit = shrinkVertically(motionScheme.defaultSpatialSpec()) + fadeOut(motionScheme.fastEffects)
            ) {
                Text(
                    text = "${parsed.from}/… → ${parsed.to}/…\nThe rest of the link stays the same. Trackers are removed first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun DomainField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isError: Boolean,
    errorText: String,
    readOnly: Boolean = false,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.trim()) },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = isError,
        readOnly = readOnly,
        enabled = !readOnly || value.isNotBlank(),
        supportingText = if (isError) {
            { Text(errorText) }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = GoogleSansCode,
            color = MaterialTheme.colorScheme.onSurface
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = imeAction,
            autoCorrectEnabled = false
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Small tonal chip with the shared squash + drag-wobble press feel. */
@Composable
fun SuggestionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motionScheme = LocalMotionScheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scaleX by animateFloatAsState(if (pressed) 0.96f else 1f, motionScheme.fastSpatial, label = "chipScaleX")
    val scaleY by animateFloatAsState(if (pressed) 0.9f else 1f, motionScheme.fastSpatial, label = "chipScaleY")
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
            }
            .expressiveDragWobble()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
