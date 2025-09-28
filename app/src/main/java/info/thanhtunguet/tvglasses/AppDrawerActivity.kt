package info.thanhtunguet.tvglasses

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: android.graphics.drawable.Drawable
)

class AppDrawerActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private lateinit var usbDetectionManager: UsbDetectionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)
        setupUsbDetection()
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        recyclerView = findViewById(R.id.recyclerViewApps)
        recyclerView.layoutManager = GridLayoutManager(this, 4)
        
        loadInstalledApps()
        
        // Add refresh functionality (long press on toolbar title)
        toolbar.setOnLongClickListener {
            refreshApps()
            true
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun loadInstalledApps() {
        val packageManager = packageManager
        val apps = mutableListOf<AppInfo>()
        val processedPackages = mutableSetOf<String>()
        
        // Method 1: Get apps with LAUNCHER category (most common)
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherApps = packageManager.queryIntentActivities(launcherIntent, 0)
        
        for (resolveInfo in launcherApps) {
            val activityInfo = resolveInfo.activityInfo
            val packageName = activityInfo.packageName
            
            // Skip our own app and already processed packages
            if (packageName == this.packageName || processedPackages.contains(packageName)) {
                continue
            }
            
            // Skip system launcher apps that shouldn't be shown
            if (isSystemLauncher(packageName)) {
                continue
            }
            
            try {
                val appInfo = AppInfo(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = packageName,
                    activityName = activityInfo.name,
                    icon = resolveInfo.loadIcon(packageManager)
                )
                apps.add(appInfo)
                processedPackages.add(packageName)
            } catch (e: Exception) {
                android.util.Log.w("AppDrawer", "Error loading app info for $packageName", e)
            }
        }
        
        // Method 2: Get apps from all installed packages (fallback for missed apps)
        try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            
            for (packageInfo in installedPackages) {
                val packageName = packageInfo.packageName
                
                // Skip already processed packages and our own app
                if (processedPackages.contains(packageName) || packageName == this.packageName) {
                    continue
                }
                
                // Check if package has a launch intent
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    try {
                        val applicationInfo = packageInfo.applicationInfo
                        if (applicationInfo != null) {
                            val appInfo = AppInfo(
                                label = applicationInfo.loadLabel(packageManager).toString(),
                                packageName = packageName,
                                activityName = launchIntent.component?.className ?: "",
                                icon = applicationInfo.loadIcon(packageManager)
                                )
                            apps.add(appInfo)
                            processedPackages.add(packageName)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AppDrawer", "Error loading package info for $packageName", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppDrawer", "Error getting installed packages", e)
        }
        
        // Method 3: Look for apps with other categories (Android TV apps, etc.)
        val tvIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val tvApps = packageManager.queryIntentActivities(tvIntent, 0)
        
        for (resolveInfo in tvApps) {
            val activityInfo = resolveInfo.activityInfo
            val packageName = activityInfo.packageName
            
            if (packageName == this.packageName || processedPackages.contains(packageName)) {
                continue
            }
            
            try {
                val appInfo = AppInfo(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = packageName,
                    activityName = activityInfo.name,
                    icon = resolveInfo.loadIcon(packageManager)
                )
                apps.add(appInfo)
                processedPackages.add(packageName)
            } catch (e: Exception) {
                android.util.Log.w("AppDrawer", "Error loading TV app info for $packageName", e)
            }
        }
        
        android.util.Log.d("AppDrawer", "Found ${apps.size} apps total")
        
        // Filter out duplicates by package name (keep the first occurrence)
        val uniqueApps = apps.distinctBy { it.packageName }.toMutableList()
        android.util.Log.d("AppDrawer", "After deduplication: ${uniqueApps.size} unique apps")
        
        // Sort unique apps alphabetically
        uniqueApps.sortBy { it.label.lowercase() }
        
        appAdapter = AppAdapter(uniqueApps) { appInfo ->
            launchApp(appInfo)
        }
        recyclerView.adapter = appAdapter
    }
    
    private fun launchApp(appInfo: AppInfo) {
        try {
            android.util.Log.d("AppDrawer", "Launching app: ${appInfo.label} (${appInfo.packageName})")
            
            // Method 1: Try to launch using explicit activity name
            if (appInfo.activityName.isNotEmpty()) {
                try {
                    val intent = Intent().apply {
                        setClassName(appInfo.packageName, appInfo.activityName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    finish() // Close app drawer after launching app
                    return
                } catch (e: Exception) {
                    android.util.Log.w("AppDrawer", "Failed to launch ${appInfo.packageName} with explicit activity", e)
                }
            }
            
            // Method 2: Try to launch using package manager's launch intent
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    finish()
                    return
                }
            } catch (e: Exception) {
                android.util.Log.w("AppDrawer", "Failed to launch ${appInfo.packageName} with launch intent", e)
            }
            
            // Method 3: Try to launch main activity with LAUNCHER category
            try {
                val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(appInfo.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
                if (resolveInfos.isNotEmpty()) {
                    val activityInfo = resolveInfos[0].activityInfo
                    mainIntent.setClassName(activityInfo.packageName, activityInfo.name)
                    startActivity(mainIntent)
                    finish()
                    return
                }
            } catch (e: Exception) {
                android.util.Log.w("AppDrawer", "Failed to launch ${appInfo.packageName} with main intent", e)
            }
            
            // Method 4: Try Android TV leanback launcher intent
            try {
                val tvIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    setPackage(appInfo.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                val resolveInfos = packageManager.queryIntentActivities(tvIntent, 0)
                if (resolveInfos.isNotEmpty()) {
                    val activityInfo = resolveInfos[0].activityInfo
                    tvIntent.setClassName(activityInfo.packageName, activityInfo.name)
                    startActivity(tvIntent)
                    finish()
                    return
                }
            } catch (e: Exception) {
                android.util.Log.w("AppDrawer", "Failed to launch ${appInfo.packageName} with TV intent", e)
            }
            
            // If all methods fail, show error
            android.util.Log.e("AppDrawer", "Failed to launch app: ${appInfo.label}")
            android.widget.Toast.makeText(this, "Failed to launch ${appInfo.label}", android.widget.Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            android.util.Log.e("AppDrawer", "Error launching app: ${appInfo.label}", e)
            android.widget.Toast.makeText(this, "Error launching ${appInfo.label}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupUsbDetection() {
        usbDetectionManager = UsbDetectionManager.createWithAutoNavigation(
            context = this,
            onUsbStateChanged = { isConnected ->
                // Optional: Add any UI changes when USB is connected/disconnected
            }
        )
        
        lifecycle.addObserver(usbDetectionManager)
    }
    
    private fun isSystemLauncher(packageName: String): Boolean {
        // Common system launcher packages that shouldn't appear in app drawer
        val systemLaunchers = setOf(
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.launcher",
            "com.samsung.android.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.oneplus.launcher",
            "com.sonymobile.home",
            "com.lge.launcher2",
            "com.htc.launcher",
            "com.android.settings", // Settings shouldn't be in app drawer
            "android" // System android package
        )
        
        return systemLaunchers.contains(packageName) || 
               packageName.contains("launcher") && packageName.contains("android")
    }
    
    private fun refreshApps() {
        android.util.Log.d("AppDrawer", "Refreshing app list...")
        android.widget.Toast.makeText(this, "Refreshing apps...", android.widget.Toast.LENGTH_SHORT).show()
        loadInstalledApps()
    }
}

class AppAdapter(
    private val apps: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {
    
    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imageAppIcon)
        val label: TextView = view.findViewById(R.id.textAppLabel)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        
        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
    }
    
    override fun getItemCount(): Int = apps.size
}