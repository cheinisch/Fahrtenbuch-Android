package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fahrtenbuch.mobile.R
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.ui.AppViewModel

@Composable
fun StatisticsScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val totalKm = state.trips.sumOf { it.distanceKm ?: 0.0 }
    val businessKm = state.trips.filter { it.tripType.name == "BUSINESS" }.sumOf { it.distanceKm ?: 0.0 }
    val privateKm = state.trips.filter { it.tripType.name == "PRIVATE" }.sumOf { it.distanceKm ?: 0.0 }
    val commuteKm = state.trips.filter { it.tripType.name == "COMMUTE" }.sumOf { it.distanceKm ?: 0.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(stringResource(R.string.statistics), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.statistics_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatisticCard(stringResource(R.string.statistics_total), "%.1f km".format(totalKm), Modifier.weight(1f))
                StatisticCard(stringResource(R.string.statistics_trips), state.trips.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            StatisticCard(stringResource(R.string.statistics_business), "%.1f km".format(businessKm), Modifier.fillMaxWidth())
        }
        item {
            StatisticCard(stringResource(R.string.statistics_private), "%.1f km".format(privateKm), Modifier.fillMaxWidth())
        }
        item {
            StatisticCard(stringResource(R.string.statistics_commute), "%.1f km".format(commuteKm), Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatisticCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
