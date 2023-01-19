package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Database


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ControlsList(
    modifier: Modifier = Modifier,
    state: String,
    databases: List<Database>,
    unselectedDatabaseUrls: List<String>,
    shouldShowExplicitSongs: Boolean,
    onDatabaseEnabledChanged: (Database, Boolean) -> Unit,
    onDatabaseSelectedChanged: (Database, Boolean) -> Unit,
    onShouldShowExplicitSongsChanged: (Boolean) -> Unit,
    onForceRefreshPressed: () -> Unit,
    onDeleteLocalDataPressed: () -> Unit
) = LazyColumn(
    modifier = modifier.fillMaxHeight()
) {
    if (databases.isNotEmpty()) {
        item(key = "header_databases") {
            Header(
                modifier = Modifier.animateItemPlacement(),
                text = "All databases"
            )
        }
        itemsIndexed(
            items = databases,
            key = { _, database -> "database_${database.url}" }
        ) { _, database ->
            CheckboxItem(
                modifier = Modifier.animateItemPlacement(),
                text = database.name,
                isChecked = database.isEnabled,
                onCheckedChanged = { onDatabaseEnabledChanged(database, it) }
            )
        }
        val enabledDatabases = databases.filter { it.isEnabled }
        if (enabledDatabases.isNotEmpty()) {
            item(key = "header_filters") {
                Header(
                    modifier = Modifier.animateItemPlacement(),
                    text = "Filters"
                )
            }
            item(key = "filter_explicit") {
                CheckboxItem(
                    modifier = Modifier.animateItemPlacement(),
                    text = "Show explicit songs",
                    isChecked = shouldShowExplicitSongs,
                    onCheckedChanged = onShouldShowExplicitSongsChanged
                )
            }
            itemsIndexed(
                items = enabledDatabases,
                key = { _, database -> "filter_database_${database.url}" }
            ) { _, database ->
                CheckboxItem(
                    modifier = Modifier.animateItemPlacement(),
                    text = "Database ${database.name}",
                    isChecked = !unselectedDatabaseUrls.contains(database.url),
                    onCheckedChanged = { onDatabaseSelectedChanged(database, it) }
                )
            }
        }
        item(key = "header_state") {
            Header(
                modifier = Modifier.animateItemPlacement(),
                text = "State: $state"
            )
        }
        item(key = "force_refresh") {
            ClickableControl(
                modifier = Modifier.animateItemPlacement(),
                text = "Force refresh",
                onClick = { onForceRefreshPressed() }
            )
        }
        item(key = "delete_local_data") {
            ClickableControl(
                modifier = Modifier.animateItemPlacement(),
                text = "Delete local data",
                onClick = { onDeleteLocalDataPressed() }
            )
        }
    }
}

@Composable
private fun CheckboxItem(
    modifier: Modifier = Modifier,
    text: String,
    isChecked: Boolean,
    onCheckedChanged: (Boolean) -> Unit
) = RoundedCard(
    modifier = modifier
) {
    Row(
        modifier = Modifier.clickable { onCheckedChanged(!isChecked) }
    ) {
        Checkbox(
            modifier = Modifier.align(Alignment.CenterVertically),
            checked = isChecked,
            onCheckedChange = onCheckedChanged
        )
        Text(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically).padding(vertical = 8.dp).padding(end = 8.dp),
            text = text
        )
    }
}

@Composable
private fun ClickableControl(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit
) = RoundedCard(
    modifier = modifier
) {
    Text(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(8.dp),
        text = text
    )
}