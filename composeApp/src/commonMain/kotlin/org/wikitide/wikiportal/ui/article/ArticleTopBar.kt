package org.wikitide.wikiportal.ui.article

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.article_overflow_find_on_page
import org.wikitide.wikiportal.resources.article_top_bar_close_search
import org.wikitide.wikiportal.resources.article_top_bar_next_match
import org.wikitide.wikiportal.resources.article_top_bar_previous_match
import org.wikitide.wikiportal.resources.article_top_bar_save_for_later
import org.wikitide.wikiportal.resources.article_top_bar_tabs
import org.wikitide.wikiportal.resources.article_top_bar_unsave
import org.wikitide.wikiportal.resources.common_close
import org.wikitide.wikiportal.resources.common_more_options

/** The "Find on page" top bar, shown instead of [ArticleTopBar] while a page search is active. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    activeIndex: Int,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    onSearchSubmit: () -> Unit,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.article_top_bar_close_search))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(Res.string.article_overflow_find_on_page)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
            )
        },
        actions = {
            if (query.isNotBlank()) {
                Text(
                    text = if (matchCount > 0) "$activeIndex/$matchCount" else "0/0",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            IconButton(enabled = matchCount > 0, onClick = onPreviousMatch) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(Res.string.article_top_bar_previous_match))
            }

            IconButton(enabled = matchCount > 0, onClick = onNextMatch) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(Res.string.article_top_bar_next_match))
            }
        },
        windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/**
 * The reader's normal top bar: close, page title, save toggle, tab
 * switcher with an open-tab count badge, and the overflow menu trigger.
 * [overflowMenu] is a slot for [ArticleOverflowMenu] rather than a
 * fixed part of this composable, since its own expanded/dismiss state
 * lives with the caller alongside the other menu-triggered actions,
 * for example opening the search bar this bar can also switch to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleTopBar(
    displayedTitle: String,
    openTabCount: Int,
    isSaved: Boolean,
    onClose: () -> Unit,
    onToggleSaved: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onOpenOverflowMenu: () -> Unit,
    overflowMenu: @Composable () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.common_close))
            }
        },
        title = {
            Text(
                text = displayedTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        actions = {
            IconButton(onClick = onToggleSaved) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (isSaved) stringResource(Res.string.article_top_bar_unsave) else stringResource(Res.string.article_top_bar_save_for_later),
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }

            IconButton(onClick = onOpenTabSwitcher) {
                BadgedBox(
                    badge = { if (openTabCount > 0) Badge { Text("$openTabCount") } },
                ) {
                    Icon(Icons.Filled.Tab, contentDescription = stringResource(Res.string.article_top_bar_tabs))
                }
            }

            IconButton(onClick = onOpenOverflowMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.common_more_options))
            }

            overflowMenu()
        },
        windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
    )
}
