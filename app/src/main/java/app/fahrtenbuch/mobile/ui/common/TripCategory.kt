package app.fahrtenbuch.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.SpecialCategory
import app.fahrtenbuch.mobile.data.model.TripType

@Composable
internal fun localizedTripType(type: TripType): String = when (type) {
    TripType.BUSINESS -> stringResource(R.string.trip_business)
    TripType.PRIVATE -> stringResource(R.string.trip_private)
    TripType.COMMUTE -> stringResource(R.string.trip_commute)
    TripType.UNCLASSIFIED -> stringResource(R.string.trip_unclassified)
}

@Immutable
internal data class CategoryColors(val container: Color, val content: Color)

/** Feste Farbe je Kategorie, in Hell und Dunkel gut lesbar (Blau, Lila, Türkis, Bernstein). */
@Composable
internal fun categoryColors(type: TripType): CategoryColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (type) {
        TripType.BUSINESS -> if (dark) CategoryColors(Color(0xFF1C2A38), Color(0xFF9ED1FF))
            else CategoryColors(Color(0xFFDCEBFA), Color(0xFF0B4F86))
        TripType.PRIVATE -> if (dark) CategoryColors(Color(0xFF2A2138), Color(0xFFCDB8FF))
            else CategoryColors(Color(0xFFEDE5FB), Color(0xFF5B3A9E))
        TripType.COMMUTE -> if (dark) CategoryColors(Color(0xFF16302B), Color(0xFF8FE3CB))
            else CategoryColors(Color(0xFFD9F3EB), Color(0xFF0E6B52))
        TripType.UNCLASSIFIED -> if (dark) CategoryColors(Color(0xFF3A2E17), Color(0xFFF7C873))
            else CategoryColors(Color(0xFFFCEFD6), Color(0xFF8A5A00))
    }
}

@Composable
internal fun CategoryChip(type: TripType, modifier: Modifier = Modifier) {
    val colors = categoryColors(type)
    Text(
        localizedTripType(type),
        modifier = modifier
            .background(colors.container, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = colors.content,
        maxLines = 1
    )
}

@Composable
internal fun CategoryIcon(type: TripType, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = categoryColors(type)
    Box(
        modifier = modifier
            .size(size)
            .background(colors.container, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = colors.content, modifier = Modifier.size(size / 2))
    }
}

// ---------------------------------------------------------------------
// Sonderkategorien (zweite Ebene, z. B. Medizinisch, Ehrenamt)
// ---------------------------------------------------------------------

@Composable
internal fun localizedSpecialCategory(category: SpecialCategory): String = stringResource(
    when (category) {
        SpecialCategory.MEDICAL -> R.string.special_medical
        SpecialCategory.VOLUNTEER -> R.string.special_volunteer
        SpecialCategory.EDUCATION -> R.string.special_education
        SpecialCategory.APPLICATION -> R.string.special_application
        SpecialCategory.MOVING -> R.string.special_moving
        SpecialCategory.OTHER -> R.string.special_other
    }
)

internal fun specialCategoryIcon(category: SpecialCategory): ImageVector = when (category) {
    SpecialCategory.MEDICAL -> Icons.Default.MedicalServices
    SpecialCategory.VOLUNTEER -> Icons.Default.VolunteerActivism
    SpecialCategory.EDUCATION -> Icons.Default.School
    SpecialCategory.APPLICATION -> Icons.Default.Badge
    SpecialCategory.MOVING -> Icons.Default.LocalShipping
    SpecialCategory.OTHER -> Icons.Default.MoreHoriz
}

/** Eigene Farben je Sonderkategorie, in Hell und Dunkel lesbar. */
@Composable
internal fun specialCategoryColors(category: SpecialCategory): CategoryColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (category) {
        SpecialCategory.MEDICAL -> if (dark) CategoryColors(Color(0xFF3A2A12), Color(0xFFF5B547))
            else CategoryColors(Color(0xFFFCEBCB), Color(0xFF7A4E00))
        SpecialCategory.VOLUNTEER -> if (dark) CategoryColors(Color(0xFF3A1D1D), Color(0xFFFF8A80))
            else CategoryColors(Color(0xFFFBE0DE), Color(0xFFA1271D))
        SpecialCategory.EDUCATION -> if (dark) CategoryColors(Color(0xFF1C2A38), Color(0xFF8CC8FF))
            else CategoryColors(Color(0xFFDCEBFA), Color(0xFF0B4F86))
        SpecialCategory.APPLICATION -> if (dark) CategoryColors(Color(0xFF2A2138), Color(0xFFCDB8FF))
            else CategoryColors(Color(0xFFEDE5FB), Color(0xFF5B3A9E))
        SpecialCategory.MOVING -> if (dark) CategoryColors(Color(0xFF16302B), Color(0xFF8FE3CB))
            else CategoryColors(Color(0xFFD9F3EB), Color(0xFF0E6B52))
        SpecialCategory.OTHER -> if (dark) CategoryColors(Color(0xFF262B33), Color(0xFFB8C2CE))
            else CategoryColors(Color(0xFFE6E9EE), Color(0xFF3F4955))
    }
}

/** Kleiner Chip mit Symbol, z. B. in Liste und Details. */
@Composable
internal fun SpecialCategoryChip(category: SpecialCategory, modifier: Modifier = Modifier) {
    val colors = specialCategoryColors(category)
    Row(
        modifier = modifier
            .background(colors.container, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(specialCategoryIcon(category), contentDescription = null, tint = colors.content, modifier = Modifier.size(12.dp))
        Text(
            localizedSpecialCategory(category),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.content,
            maxLines = 1
        )
    }
}
