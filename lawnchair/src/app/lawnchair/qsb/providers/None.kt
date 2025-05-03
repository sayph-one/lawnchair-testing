package app.lawnchair.qsb.providers

import com.android.launcher3.R

object None : QsbSearchProvider(
    id = "none",
    name = R.string.search_provider_none,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    className = null,
    action = null,
    website = "",
    type = QsbSearchProviderType.LOCAL
)

