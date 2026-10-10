package com.specsnap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.specsnap.domain.model.ComponentResponse
import com.specsnap.domain.model.PinoutEntry

private enum class ComponentDetailTab(val label: String) {
    OVERVIEW("Overview"),
    PINOUT("Pinout"),
    ELECTRICAL("Electrical"),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ComponentDetailSheet(
    component: ComponentResponse,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember(component) { mutableStateOf(ComponentDetailTab.OVERVIEW) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            ComponentSheetHeader(component = component, onDismiss = onDismiss)
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                ComponentDetailTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (selectedTab) {
                ComponentDetailTab.OVERVIEW -> OverviewTab(component, Modifier.weight(1f))
                ComponentDetailTab.PINOUT -> PinoutTab(component.pinouts, Modifier.weight(1f))
                ComponentDetailTab.ELECTRICAL -> ElectricalTab(component, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ComponentSheetHeader(
    component: ComponentResponse,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = component.partNumber,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = component.manufacturer,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDismiss) { Text("Close") }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = component.category,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun OverviewTab(component: ComponentResponse, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Description", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(component.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            DetailField(label = "Category", value = component.category)
        }
        if (component.safetyAlerts.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Safety alerts", style = MaterialTheme.typography.titleMedium)
                    component.safetyAlerts.forEach { alert ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = alert,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinoutTab(pinouts: List<PinoutEntry>, modifier: Modifier = Modifier) {
    if (pinouts.isEmpty()) {
        EmptyTabMessage("No pin assignments could be identified from this scan.", modifier)
        return
    }

    var selectedPin by remember(pinouts) { mutableStateOf<Int?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "pinout-header") {
            PinoutRow(
                pinout = PinoutEntry(0, "Name", "Function"),
                selected = false,
                isHeader = true,
                onClick = {},
            )
        }
        itemsIndexed(pinouts, key = { index, pin -> "${pin.pinNumber}-$index-${pin.pinName}" }) { _, pinout ->
            PinoutRow(
                pinout = pinout,
                selected = selectedPin == pinout.pinNumber,
                onClick = {
                    selectedPin = if (selectedPin == pinout.pinNumber) null else pinout.pinNumber
                },
            )
        }
        if (selectedPin != null) {
            val selected = pinouts.firstOrNull { it.pinNumber == selectedPin }
            if (selected != null) {
                item(key = "selected-pin-detail") {
                    DetailField(
                        label = "Pin ${selected.pinNumber} · ${selected.pinName}",
                        value = selected.function,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinoutRow(
    pinout: PinoutEntry,
    selected: Boolean,
    isHeader: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        isHeader -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(10.dp))
            .then(
                if (isHeader) Modifier else Modifier.selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                ),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isHeader) "Pin" else pinout.pinNumber.toString(),
            modifier = Modifier.weight(0.55f),
            style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = pinout.pinName,
            modifier = Modifier.weight(1.1f),
            style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = pinout.function,
            modifier = Modifier.weight(2f),
            style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ElectricalTab(component: ComponentResponse, modifier: Modifier = Modifier) {
    val ratings = component.electricalRatings
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DetailField("Minimum voltage", ratings.minVoltage) }
        item { DetailField("Maximum voltage", ratings.maxVoltage) }
        item { DetailField("Maximum current", ratings.maxCurrent) }
        item { DetailField("Operating temperature", ratings.operatingTempRange) }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun EmptyTabMessage(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
