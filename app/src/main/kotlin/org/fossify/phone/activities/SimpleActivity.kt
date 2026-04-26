package org.fossify.phone.activities

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.phone.R

open class SimpleActivity : BaseSimpleActivity() {

    // Commons compares getPackageName() against "org.fossify." to detect modded
    // builds and shows a "fake version" popup if it doesn't match. Our applicationId
    // is com.nabilnazer.phone.debug (renamed in Build 1 to avoid collision with
    // upstream Fossify Phone), so the check fails and the popup keeps appearing.
    // Returning the source-code namespace here makes the in-process Commons check
    // pass while leaving the real applicationId untouched at the OS level (intent
    // resolution, Settings → Apps, etc. all still see com.nabilnazer.phone.debug
    // because the framework reads the real package name through different channels).
    override fun getPackageName(): String = "org.fossify.phone"

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher_red,
        R.mipmap.ic_launcher_pink,
        R.mipmap.ic_launcher_purple,
        R.mipmap.ic_launcher_deep_purple,
        R.mipmap.ic_launcher_indigo,
        R.mipmap.ic_launcher_blue,
        R.mipmap.ic_launcher_light_blue,
        R.mipmap.ic_launcher_cyan,
        R.mipmap.ic_launcher_teal,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_light_green,
        R.mipmap.ic_launcher_lime,
        R.mipmap.ic_launcher_yellow,
        R.mipmap.ic_launcher_amber,
        R.mipmap.ic_launcher_orange,
        R.mipmap.ic_launcher_deep_orange,
        R.mipmap.ic_launcher_brown,
        R.mipmap.ic_launcher_blue_grey,
        R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "Phone"
}
