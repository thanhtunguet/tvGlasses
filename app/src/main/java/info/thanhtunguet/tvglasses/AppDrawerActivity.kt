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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        recyclerView = findViewById(R.id.recyclerViewApps)
        recyclerView.layoutManager = GridLayoutManager(this, 4)
        
        loadInstalledApps()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun loadInstalledApps() {
        val packageManager = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfos = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val apps = mutableListOf<AppInfo>()
        
        for (resolveInfo in resolveInfos) {
            val activityInfo = resolveInfo.activityInfo
            
            // Skip our own app to avoid recursion
            if (activityInfo.packageName == packageName) {
                continue
            }
            
            val appInfo = AppInfo(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = activityInfo.packageName,
                activityName = activityInfo.name,
                icon = resolveInfo.loadIcon(packageManager)
            )
            apps.add(appInfo)
        }
        
        // Sort apps alphabetically
        apps.sortBy { it.label.lowercase() }
        
        appAdapter = AppAdapter(apps) { appInfo ->
            launchApp(appInfo)
        }
        recyclerView.adapter = appAdapter
    }
    
    private fun launchApp(appInfo: AppInfo) {
        try {
            val intent = Intent().apply {
                setClassName(appInfo.packageName, appInfo.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            finish() // Close app drawer after launching app
        } catch (e: Exception) {
            // Fallback: try to launch app using package manager
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    finish()
                }
            } catch (e: Exception) {
                // App launch failed
            }
        }
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