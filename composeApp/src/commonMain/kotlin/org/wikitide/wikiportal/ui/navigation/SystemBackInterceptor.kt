package org.wikitide.wikiportal.ui.navigation

import androidx.compose.runtime.Composable

/**
 * Consumes the system back gesture or button directly, bypassing
 * whatever transition preview the platform's navigation host would
 * otherwise show for it, whenever [enabled] is true.
 *
 * This is deliberately not animated. When [enabled] is true, meaning
 * in-page WebView history is available, or the tab switcher is open,
 * back is handled with an immediate cut, with no predictive back
 * preview at all. Only when [enabled] is false, meaning back is a
 * genuine pop to whatever is under ArticleRoute, Dashboard, does the
 * gesture fall through to NavDisplay's own callback, which still gets
 * its normal animated predictive pop.
 *
 * This does nothing on platforms without a system predictive back
 * gesture, IOS, Desktop and Web. Those are already handled correctly by
 * NavDisplay's plain onBack callback alone.
 */
@Composable
expect fun SystemBackInterceptor(enabled: Boolean, onBack: () -> Unit)
