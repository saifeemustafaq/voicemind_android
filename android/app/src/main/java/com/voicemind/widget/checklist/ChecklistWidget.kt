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
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.voicemind.MainActivity
import com.voicemind.R
import com.voicemind.data.local.entity.ActionItemEntity
import com.voicemind.widget.common.PromptContent
import com.voicemind.widget.common.WidgetColors
import com.voicemind.widget.common.WidgetTitle
import dagger.hilt.android.EntryPointAccessors

class ChecklistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ChecklistWidgetEntryPoint::class.java,
        )
        val dao = entryPoint.actionItemDao()
        val allItems = dao.getAllNonDeleted()

        provideContent {
            val prefs = currentState<Preferences>()
            val isSignedIn = prefs[ChecklistWidgetStateKeys.IS_SIGNED_IN] ?: false
            val showCompleted = prefs[ChecklistWidgetStateKeys.SHOW_COMPLETED] ?: false
            val selectedTab = prefs[ChecklistWidgetStateKeys.SELECTED_TAB]
                ?: ChecklistWidgetStateKeys.TAB_TODO

            val (todoItems, doneItems) = allItems.partition { !it.completed }

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
    todoItems: List<ActionItemEntity>,
    doneItems: List<ActionItemEntity>,
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
    todoItems: List<ActionItemEntity>,
    doneItems: List<ActionItemEntity>,
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
private fun TaskList(items: List<ActionItemEntity>) {
    LazyColumn {
        items(items = items, itemId = { it.id.fold(0L) { acc, c -> acc * 31L + c.code } }) { item ->
            TaskRow(item)
        }
    }
}

@Composable
private fun TaskRow(item: ActionItemEntity) {
    CheckBox(
        checked = item.completed,
        onCheckedChange = actionRunCallback<ToggleItemAction>(
            actionParametersOf(
                ToggleItemAction.ItemIdKey to item.id,
                ToggleItemAction.CurrentCompletedKey to item.completed,
            )
        ),
        text = item.title,
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

@Composable
private fun EmptyState(isDoneTab: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
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
