/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.R
import com.convx.music.constants.SearchSource
import com.convx.music.ui.component.NavSearchState
import com.convx.music.ui.component.SymbolIcon
import com.convx.music.ui.component.sf
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.foundation.IosTheme

@Composable
internal fun SearchInputField(state: NavSearchState) {
    val colors = IosTheme.colors
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val closeKeyboard = {
        focusManager.clearFocus()
        keyboard?.hide()
        state.onCloseKeyboard()
    }
    BackHandler(enabled = state.keyboardActive, onBack = closeKeyboard)

    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(46.dp)
            .clip(ContinuousRoundedRectangle(14.dp))
            .background(colors.secondarySystemBackground)
            .padding(start = 13.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SymbolIcon(
            symbol = sf("magnifyingglass"),
            contentDescription = null,
            tint = colors.secondaryLabel,
            size = 17.dp,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (state.query.text.isEmpty()) {
                Text(stringResource(R.string.search), color = colors.secondaryLabel, fontSize = 16.sp)
            }
            BasicTextField(
                value = state.query,
                onValueChange = state.onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.label, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.label),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val query = state.query.text.trim()
                    if (query.isNotEmpty()) {
                        closeKeyboard()
                        state.onSubmit(query)
                    }
                }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                    if (focus.isFocused && !state.keyboardActive) state.onTapBar()
                },
            )
        }
        if (state.query.text.isNotEmpty()) {
            IconButton(onClick = { state.onQueryChange(TextFieldValue()) }, modifier = Modifier.size(36.dp)) {
                SymbolIcon(symbol = sf("xmark.circle.fill"), contentDescription = stringResource(R.string.clear), size = 17.dp)
            }
        }
        if (state.canToggleSource) {
            val online = state.searchSource == SearchSource.ONLINE
            IconButton(onClick = state.onToggleSource, modifier = Modifier.size(36.dp)) {
                SymbolIcon(
                    symbol = sf(if (online) "globe" else "music.note.list"),
                    contentDescription = stringResource(if (online) R.string.filter_library else R.string.search),
                    size = 18.dp,
                )
            }
        }
    }
}
