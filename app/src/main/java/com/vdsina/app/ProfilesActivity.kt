package com.vdsina.app

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.vdsina.app.databinding.ActivityProfilesBinding

class ProfilesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilesBinding
    private lateinit var profileManager: ProfileManager
    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.profiles_title)

        profileManager = ProfileManager.getInstance(this)

        adapter = ProfileAdapter(
            profiles = profileManager.getProfiles().toMutableList(),
            currentProfileId = profileManager.getCurrentProfileId(),
            onProfileClick = { profile -> selectProfile(profile) },
            onEditClick = { profile -> showEditDialog(profile) },
            onDeleteClick = { profile -> confirmDelete(profile) }
        )

        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            showEditDialog(null)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun selectProfile(profile: Profile) {
        if (profileManager.isCurrentProfile(profile.id)) {
            finish()
            return
        }
        profileManager.switchToProfile(profile.id)
        adapter.currentProfileId = profile.id
        adapter.notifyDataSetChanged()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun showEditDialog(profile: Profile?) {
        val isNew = profile == null
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)

        val editName = dialogView.findViewById<TextInputEditText>(R.id.editName)
        val editUrl = dialogView.findViewById<TextInputEditText>(R.id.editUrl)
        val editLogin = dialogView.findViewById<TextInputEditText>(R.id.editLogin)
        val editPassword = dialogView.findViewById<TextInputEditText>(R.id.editPassword)

        if (!isNew) {
            editName.setText(profile!!.name)
            editUrl.setText(profile.url)
            editLogin.setText(profile.login)
            editPassword.setText(profile.password)
        } else {
            editUrl.setText(Profile.DEFAULT_URL)
        }

        AlertDialog.Builder(this)
            .setTitle(if (isNew) R.string.add_profile else R.string.edit_profile)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = editName.text?.toString()?.trim() ?: ""
                val url = editUrl.text?.toString()?.trim() ?: Profile.DEFAULT_URL
                val login = editLogin.text?.toString()?.trim() ?: ""
                val password = editPassword.text?.toString() ?: ""

                if (name.isNotEmpty()) {
                    if (isNew) {
                        val newProfile = Profile(
                            id = System.currentTimeMillis().toString(),
                            name = name,
                            url = url.ifEmpty { Profile.DEFAULT_URL },
                            login = login,
                            password = password
                        )
                        profileManager.addProfile(newProfile)
                    } else {
                        profile!!.name = name
                        profile.url = url.ifEmpty { Profile.DEFAULT_URL }
                        profile.login = login
                        profile.password = password
                        profileManager.updateProfile(profile)
                    }
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(profile: Profile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile)
            .setMessage(getString(R.string.delete_profile_confirm, profile.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                profileManager.deleteProfile(profile.id)
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshList() {
        adapter.profiles.clear()
        adapter.profiles.addAll(profileManager.getProfiles())
        adapter.currentProfileId = profileManager.getCurrentProfileId()
        adapter.notifyDataSetChanged()
    }

    class ProfileAdapter(
        val profiles: MutableList<Profile>,
        var currentProfileId: String,
        private val onProfileClick: (Profile) -> Unit,
        private val onEditClick: (Profile) -> Unit,
        private val onDeleteClick: (Profile) -> Unit
    ) : RecyclerView.Adapter<ProfileAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val radioSelected: RadioButton = view.findViewById(R.id.radioSelected)
            val textName: TextView = view.findViewById(R.id.textName)
            val textUrl: TextView = view.findViewById(R.id.textUrl)
            val textLogin: TextView = view.findViewById(R.id.textLogin)
            val buttonEdit: ImageButton = view.findViewById(R.id.buttonEdit)
            val buttonDelete: ImageButton = view.findViewById(R.id.buttonDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_profile, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val profile = profiles[position]

            holder.radioSelected.isChecked = profile.id == currentProfileId
            holder.textName.text = profile.name
            holder.textUrl.text = profile.url
            holder.textLogin.text = profile.login.ifEmpty { "-" }

            holder.itemView.setOnClickListener { onProfileClick(profile) }
            holder.buttonEdit.setOnClickListener { onEditClick(profile) }
            holder.buttonDelete.setOnClickListener { onDeleteClick(profile) }

            holder.buttonDelete.visibility = if (profiles.size > 1) View.VISIBLE else View.GONE
        }

        override fun getItemCount() = profiles.size
    }
}

