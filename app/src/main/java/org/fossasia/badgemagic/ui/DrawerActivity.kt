package org.fossasia.badgemagic.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.activity_drawer.*
import kotlinx.android.synthetic.main.app_bar_drawer.*
import org.fossasia.badgemagic.R
import org.fossasia.badgemagic.core.android.log.Timber
import org.fossasia.badgemagic.extensions.setRotation
import org.fossasia.badgemagic.ui.base.BaseActivity
import org.fossasia.badgemagic.ui.base.BaseFragment
import org.fossasia.badgemagic.ui.fragments.AboutFragment
import org.fossasia.badgemagic.ui.fragments.DrawFragment
import org.fossasia.badgemagic.ui.fragments.SavedBadgesFragment
import org.fossasia.badgemagic.ui.fragments.SavedClipartFragment
import org.fossasia.badgemagic.ui.fragments.SettingsFragment
import org.fossasia.badgemagic.ui.fragments.TextArtFragment
import org.fossasia.badgemagic.util.SendingUtils
import org.fossasia.badgemagic.util.StorageUtils
import org.fossasia.badgemagic.viewmodels.DrawerViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class DrawerActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val PICK_FILE_RESULT_CODE = 2
        private const val REQUEST_PERMISSION_CODE = 10
        private const val REQUEST_ENABLE_BT = 1
    }

    private var showMenu: Menu? = null
    private var drawerCheckedID = R.id.create
    private var isItemCheckedNew = false

    private val viewModel by viewModel<DrawerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_drawer)

        if (intent.action == Intent.ACTION_VIEW)
            importFile(intent)

        setupDrawerAndToolbar()

        prepareForScan()
    }

    private fun setupDrawerAndToolbar() {
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        Timber.e {
            "OnCreate, ${viewModel.swappingOrientation}"
        }

        drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            }

            override fun onDrawerClosed(drawerView: View) {
                if (isItemCheckedNew) {
                    when (drawerCheckedID) {
                        R.id.create -> {
                            viewModel.swappingOrientation = false
                            setRotation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                            switchFragment(TextArtFragment.newInstance())
                            showMenu?.setGroupVisible(R.id.saved_group, false)
                        }
                        R.id.draw -> {
                            viewModel.swappingOrientation = true
                            setRotation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                            switchFragment(DrawFragment.newInstance())
                            showMenu?.setGroupVisible(R.id.saved_group, false)
                        }
                        R.id.saved_badges -> {
                            viewModel.swappingOrientation = false
                            setRotation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                            switchFragment(SavedBadgesFragment.newInstance())
                            showMenu?.setGroupVisible(R.id.saved_group, true)
                        }
                        R.id.saved_cliparts -> {
                            viewModel.swappingOrientation = false
                            setRotation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                            switchFragment(SavedClipartFragment.newInstance())
                            showMenu?.setGroupVisible(R.id.saved_group, false)
                        }
                        R.id.settings -> {
                            viewModel.swappingOrientation = false
                            setRotation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                            switchFragment(SettingsFragment.newInstance())
                            showMenu?.setGroupVisible(R.id.saved_group, false)
                        }
                        R.id.feedback -> {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/fossasia/badge-magic-android/issues")))
                        }
                        R.id.buy -> {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sg.pslab.io")))
                        }
                        R.id.about -> {
                            switchFragment(AboutFragment.newInstance())
                            showMenu?.setGroupVisible(R.id.saved_group, false)
                        }
                    }
                    isItemCheckedNew = false
                }
            }

            override fun onDrawerOpened(drawerView: View) {
            }
        })

        nav_view.setNavigationItemSelectedListener(this)
        if (!viewModel.swappingOrientation)
            when (intent.action) {
                Intent.ACTION_MAIN, "org.fossasia.badgemagic.createBadge.shortcut" -> {
                    switchFragment(TextArtFragment.newInstance())
                    showMenu?.setGroupVisible(R.id.saved_group, false)
                    nav_view.setCheckedItem(R.id.create)
                }
                "org.fossasia.badgemagic.savedBadges.shortcut" -> {
                    switchFragment(SavedBadgesFragment.newInstance())
                    showMenu?.setGroupVisible(R.id.saved_group, true)
                    nav_view.setCheckedItem(R.id.saved_badges)
                }
            } else {
            switchFragment(DrawFragment.newInstance())
            showMenu?.setGroupVisible(R.id.saved_group, false)
            nav_view.setCheckedItem(R.id.draw)
        }
    }

    fun switchToDrawLayout() {
        drawerCheckedID = R.id.draw
        viewModel.swappingOrientation = true
        setRotation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        switchFragment(DrawFragment.newInstance())
        showMenu?.setGroupVisible(R.id.saved_group, false)
        nav_view.setCheckedItem(R.id.draw)
    }

    private fun prepareForScan() {
        if (isBleSupported()) {
            checkManifestPermission()
        } else {
            Toast.makeText(this, "BLE is not supported", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun importFile(data: Intent?) {
        showImportDialog(data?.data)
    }

    private fun showImportDialog(uri: Uri?) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_dialog))
            .setMessage("${getString(R.string.import_dialog_message)} ${StorageUtils.getFileName(this, uri
                ?: Uri.EMPTY)}")
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                if (!StorageUtils.checkIfFilePresent(this, uri)) {
                    saveImportFile(uri)
                } else
                    showOverrideDialog(uri)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        SendingUtils.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PICK_FILE_RESULT_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    importFile(data)
                }
            }
            REQUEST_ENABLE_BT -> {
                this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (resultCode == Activity.RESULT_OK) {
                    prepareForScan()
                    return
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun switchFragment(fragment: BaseFragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frag_container, fragment)
            .commit()
    }

    private fun checkManifestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Timber.i { "Coarse permission granted" }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Timber.d { "Required Permission Accepted" }
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun isBleSupported(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    private fun saveImportFile(uri: Uri?) {
        if (StorageUtils.copyFileToDirectory(this, uri)) {
            Toast.makeText(this, R.string.success_import_json, Toast.LENGTH_SHORT).show()
            viewModel.updateList()
        } else Toast.makeText(this, R.string.invalid_import_json, Toast.LENGTH_SHORT).show()
    }

    private fun showOverrideDialog(uri: Uri?) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_dialog_already_present))
            .setMessage(getString(R.string.save_dialog_already_present_override))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                saveImportFile(uri)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        showMenu = menu
        val inflater = menuInflater
        inflater.inflate(R.menu.save_menu, menu)
        menu?.setGroupVisible(R.id.saved_group, false)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_import -> {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "text/plain"
                }
                startActivityForResult(intent, PICK_FILE_RESULT_CODE)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        isItemCheckedNew = true
        drawerCheckedID = item.itemId
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
