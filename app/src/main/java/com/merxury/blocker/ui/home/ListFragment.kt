package com.merxury.blocker.ui.home

import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.merxury.blocker.R
import com.merxury.blocker.baseview.ContextMenuRecyclerView
import com.merxury.blocker.ui.Constants
import com.merxury.blocker.ui.component.ComponentActivity
import com.merxury.blocker.util.ToastUtil
import com.merxury.libkit.entity.Application
import com.merxury.libkit.entity.ETrimMemoryLevel
import com.merxury.libkit.utils.ApplicationUtil
import kotlinx.android.synthetic.main.app_list_item.view.*
import kotlinx.android.synthetic.main.fragment_app_list.*
import kotlinx.coroutines.*

class ListFragment : Fragment(), HomeContract.View {
    override lateinit var presenter: HomeContract.Presenter
    private var isSystem: Boolean = false
    private val imageLoadJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + imageLoadJob)

    private var itemListener: AppItemListener = object : AppItemListener {
        override fun onAppClick(application: Application) {
            presenter.openApplicationDetails(application)
        }
    }

    private lateinit var listAdapter: AppListRecyclerViewAdapter

    override fun setLoadingIndicator(active: Boolean) {
        appListSwipeLayout?.run {
            post { isRefreshing = active }
        }
    }

    override fun searchForApplication(name: String) {
        listAdapter.filter(name)
    }

    override fun showApplicationList(applications: MutableList<Application>) {
        appListFragmentRecyclerView.visibility = View.VISIBLE
        noAppContainer.visibility = View.GONE
        listAdapter.addData(applications)
    }

    override fun showNoApplication() {
        appListFragmentRecyclerView.visibility = View.GONE
        noAppContainer.visibility = View.VISIBLE
    }

    override fun showFilteringPopUpMenu() {
        PopupMenu(activity, activity?.findViewById(R.id.menu_filter)).apply {
            menuInflater.inflate(R.menu.filter_application, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.name_asc -> presenter.currentComparator = ApplicationComparatorType.ASCENDING_BY_LABEL
                    R.id.name_des -> presenter.currentComparator = ApplicationComparatorType.DESCENDING_BY_LABEL
                    R.id.installation_time -> presenter.currentComparator = ApplicationComparatorType.INSTALLATION_TIME
                    R.id.last_update_time -> presenter.currentComparator = ApplicationComparatorType.LAST_UPDATE_TIME
                    else -> presenter.currentComparator = ApplicationComparatorType.DESCENDING_BY_LABEL
                }
                presenter.loadApplicationList(context!!, isSystem)
                true
            }
            show()
        }

    }

    override fun showApplicationDetailsUi(application: Application) {
        val intent = Intent(context, ComponentActivity::class.java)
        intent.putExtra(Constants.APPLICATION, application)
        context?.startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isSystem = it.getBoolean(IS_SYSTEM)
        }
        presenter = AppListPresenter(this)
        presenter.start(requireContext())
        listAdapter = AppListRecyclerViewAdapter(itemListener)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_app_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appListFragmentRecyclerView?.apply {
            val layoutManager = LinearLayoutManager(context)
            this.layoutManager = layoutManager
            adapter = listAdapter
            itemAnimator = DefaultItemAnimator()
            registerForContextMenu(this)
        }
        appListSwipeLayout?.apply {
            setOnRefreshListener { presenter.loadApplicationList(context, isSystem) }
            setColorSchemeResources(R.color.primary)
        }
        presenter.loadApplicationList(requireContext(), isSystem)
    }

    override fun onDestroy() {
        presenter.destroy()
        imageLoadJob.cancel()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        activity?.menuInflater?.inflate(R.menu.app_list_long_click_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (!userVisibleHint) {
            return false
        }
        val position = (item.menuInfo as ContextMenuRecyclerView.RecyclerContextMenuInfo).position
        val application = listAdapter.getDataAt(position)
        val packageName = application.packageName
        when (item.itemId) {
            R.id.block_application -> presenter.blockApplication(packageName)
            R.id.unblock_application -> presenter.unblockApplication(packageName)
            R.id.launch_application -> presenter.launchApplication(packageName)
            R.id.force_stop -> presenter.forceStop(packageName)
            R.id.enable_application -> presenter.enableApplication(packageName)
            R.id.disable_application -> presenter.disableApplication(packageName)
            R.id.clear_data -> presenter.clearData(packageName)
            R.id.trim_memory -> presenter.trimMemory(packageName, ETrimMemoryLevel.COMPLETE)
            R.id.details -> presenter.showDetails(packageName)
        }
        return true
    }

    override fun showAlert(alertMessage: Int, confirmAction: () -> Unit) {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.alert)
                .setMessage(alertMessage)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel) { dialog: DialogInterface?, _: Int -> dialog?.dismiss() }
                .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int -> confirmAction() }
                .show()
    }

    override fun showError(errorMessage: Int) {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.oops)
                .setMessage(errorMessage)
                .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
    }

    override fun showToastMessage(message: String?, length: Int) {
        ToastUtil.showToast(message ?: "", length)
    }

    override fun showDataCleared() {
        Toast.makeText(context, R.string.data_cleared, Toast.LENGTH_SHORT).show()
    }

    override fun showForceStopped() {
        Toast.makeText(context, R.string.force_stopped, Toast.LENGTH_SHORT).show()
    }

    override fun updateState(packageName: String) {
        val updatedInfo = ApplicationUtil.getApplicationInfo(context!!, packageName) ?: return
        listAdapter.update(updatedInfo)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_filter -> showFilteringPopUpMenu()
            R.id.menu_refresh -> presenter.loadApplicationList(context!!, isSystem)
        }
        return true
    }

    interface AppItemListener {
        fun onAppClick(application: Application)
    }

    companion object {
        const val IS_SYSTEM: String = "IS_SYSTEM"
        fun newInstance(isSystem: Boolean): Fragment {
            val fragment = ListFragment()
            val bundle = Bundle()
            bundle.putBoolean(IS_SYSTEM, isSystem)
            fragment.arguments = bundle
            return fragment
        }

    }

    inner class AppListRecyclerViewAdapter(private val listener: AppItemListener, private var applications: MutableList<Application> = mutableListOf()) : RecyclerView.Adapter<AppListRecyclerViewAdapter.ViewHolder>() {

        private lateinit var pm: PackageManager
        private var listCopy = ArrayList<Application>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.app_list_item, parent, false)
            pm = parent.context.packageManager
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return this.applications.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindApplication(this.applications[position])
        }

        fun addData(applications: MutableList<Application>) {
            this.applications = applications
            this.listCopy = ArrayList(applications)
            notifyDataSetChanged()
        }

        fun getDataAt(position: Int): Application {
            return applications[position]
        }

        fun update(application: Application) {
            val position = getPositionByPackageName(application.packageName)
            if (position == -1) return
            applications[position] = application
            notifyItemChanged(position)

        }

        fun getPositionByPackageName(packageName: String): Int {
            applications.forEachIndexed { index, application ->
                if (application.packageName == packageName) {
                    return index
                }
            }
            return -1
        }

        fun filter(keyword: String) {
            applications = if (keyword.isEmpty()) {
                listCopy
            } else {
                listCopy.asSequence()
                        .filter { it.label.contains(keyword, true) || it.packageName.contains(keyword, true) }
                        .toMutableList()
            }
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bindApplication(application: Application) {
                view?.apply {
                    itemView.appName.text = application.label
                    itemView.packageName.text = application.packageName
                    itemView.isLongClickable = true
                    itemView.setOnClickListener { listener.onAppClick(application) }
                    val backgroundColor = if (!application.isEnabled) {
                        R.color.disabled_app_background
                    } else if (application.isBlocked) {
                        R.color.blocked_app_background
                    } else {
                        R.color.window_background
                    }
                    itemView.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
                    uiScope.launch {
                        val icon = withContext(Dispatchers.IO) {
                            application.getApplicationIcon(pm)
                        }
                        itemView.appIcon.setImageDrawable(icon)
                    }
                }
            }
        }
    }
}