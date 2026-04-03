package com.voicemind.widget.checklist

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.Image
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.voicemind.MainActivity
import com.voicemind.R
import com.voicemind.widget.common.PromptContent
import com.voicemind.widget.common.WidgetColors
import com.voicemind.widget.common.WidgetTitle
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

class ChecklistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load from DB and seed DataStore so provideContent {} starts with fresh data.
        // ToggleItemAction will subsequently write to DataStore directly, triggering
        // an immediate reactive re-render without going through WorkManager.
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ChecklistWidgetEntryPoint::class.java,
            )
            val items = entryPoint.actionItemDao().getAllNonDeleted()
                .map { WidgetItem(it.id, it.title, it.completed) }
            updateAppWidgetState(context, id) { prefs ->
                prefs[ChecklistWidgetStateKeys.ITEMS_JSON] = serializeWidgetItems(items)
            }
        } catch (e: Exception) {
            Timber.e(e, "ChecklistWidget: failed to load tasks")
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val isSignedIn = prefs[ChecklistWidgetStateKeys.IS_SIGNED_IN] ?: false
            val showCompleted = prefs[ChecklistWidgetStateKeys.SHOW_COMPLETED] ?: false
            val selectedTab = prefs[ChecklistWidgetStateKeys.SELECTED_TAB]
                ?: ChecklistWidgetStateKeys.TAB_TODO

            // Items are reactive: any DataStore write (ToggleItemAction, refreshChecklistWidgets)
            // re-triggers this composable immediately.
            val items = deserializeWidgetItems(prefs[ChecklistWidgetStateKeys.ITEMS_JSON] ?: "[]")
            val (todoItems, doneItems) = items.partition { !it.completed }

            WidgetRoot(
                isSignedIn = isSignedIn,
                showCompleted = showCompleted,
                selectedTab = selectedTab,
                todoItems = todoItems,
                doneItems = doneItems,
            )
        }
    }
}

@Composable
private fun WidgetRoot(
    isSignedIn: Boolean,
    showCompleted: Boolean,
    selectedTab: Int,
    todoItems: List<WidgetItem>,
    doneItems: List<WidgetItem>,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(12.dp),
    ) {
        if (!isSignedIn) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignedOutContent()
            }
        } else {
            ChecklistContent(showCompleted, selectedTab, todoItems, doneItems)
        }
    }
}

@Composable
private fun SignedOutContent() {
    val context = LocalContext.current
    PromptContent(
        subtitle = "Sign in to see your tasks",
        buttonLabel = "Sign In",
        intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )
}

@Composable
private fun ChecklistContent(
    showCompleted: Boolean,
    selectedTab: Int,
    todoItems: List<WidgetItem>,
    doneItems: List<WidgetItem>,
) {
    WidgetTitle(text = "Tasks")
    Spacer(modifier = GlanceModifier.height(8.dp))

    if (showCompleted) {
        TabBar(
            selectedTab = selectedTab,
            todoCount = todoItems.size,
            doneCount = doneItems.size,
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
    }

    val displayItems = if (showCompleted && selectedTab == ChecklistWidgetStateKeys.TAB_DONE) {
        doneItems
    } else {
        todoItems
    }

    if (displayItems.isEmpty()) {
        EmptyState(
            isDoneTab = showCompleted && selectedTab == ChecklistWidgetStateKeys.TAB_DONE,
        )
    } else {
        TaskList(items = displayItems)
    }
}

@Composable
private fun TabBar(selectedTab: Int, todoCount: Int, doneCount: Int) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabButton(
            label = "To Do ($todoCount)",
            isSelected = selectedTab == ChecklistWidgetStateKeys.TAB_TODO,
            tabIndex = ChecklistWidgetStateKeys.TAB_TODO,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        TabButton(
            label = "Done ($doneCount)",
            isSelected = selectedTab == ChecklistWidgetStateKeys.TAB_DONE,
            tabIndex = ChecklistWidgetStateKeys.TAB_DONE,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun TabButton(
    label: String,
    isSelected: Boolean,
    tabIndex: Int,
    modifier: GlanceModifier,
) {
    val bgColor = if (isSelected) WidgetColors.Accent else WidgetColors.AccentContainer
    val textColor = if (isSelected) WidgetColors.White else WidgetColors.Label

    Row(
        modifier = modifier
            .background(bgColor)
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(
                actionRunCallback<SwitchTabAction>(
                    actionParametersOf(SwitchTabAction.TabIndexKey to tabIndex)
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun TaskList(items: List<WidgetItem>) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items.forEachIndexed { index, item ->
            item(itemId = item.id.hashCode().toLong() and 0x7FFFFFFFL) {
                Column {
                    if (index > 0) {
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                    TaskRow(item)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(item: WidgetItem) {
    val icon = if (item.completed) R.drawable.ic_check_circle else R.drawable.ic_circle_outline
    val textColor = if (item.completed) WidgetColors.SecondaryLabel else WidgetColors.Label
    val decoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(10.dp)
            .background(WidgetColors.AccentContainer)
            .clickable(
                actionRunCallback<ToggleItemAction>(
                    actionParametersOf(
                        ToggleItemAction.ItemIdKey to item.id,
                        ToggleItemAction.CurrentCompletedKey to item.completed,
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = if (item.completed) "Completed" else "Incomplete",
            modifier = GlanceModifier.size(22.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Text(
            text = item.title,
            style = TextStyle(
                color = textColor,
                fontSize = 15.sp,
                textDecoration = decoration,
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun EmptyState(isDoneTab: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isDoneTab) "All done!" else "No tasks yet",
            style = TextStyle(
                color = WidgetColors.SecondaryLabel,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
