package com.project.vault.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.project.vault.R
import com.project.vault.databinding.ItemCredentialBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for the credential list.
 *
 * Uses [ListAdapter] with [DiffUtil] for efficient, animated updates.
 * Loading state per item is applied externally via [setButtonLoading]
 * without triggering a full rebind.
 */
class CredentialAdapter(
    private val onCardClick: (Credential) -> Unit,
    private val onSyncClick: (Credential) -> Unit,
    private val onShareClick: (Credential) -> Unit
) : ListAdapter<Credential, CredentialAdapter.CredentialViewHolder>(DIFF_CALLBACK) {

    // Tracks which buttons are currently in a loading state
    private val syncLoadingIds  = mutableSetOf<Int>()
    private val shareLoadingIds = mutableSetOf<Int>()

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Updates the loading state of a single item's button without rebinding
     * the entire list. Called by the Fragment when [HomeViewModel.buttonLoadingEvent] fires.
     */
    fun setButtonLoading(
        credentialId: Int,
        buttonType: HomeViewModel.ButtonType,
        isLoading: Boolean
    ) {
        when (buttonType) {
            HomeViewModel.ButtonType.SYNC  -> if (isLoading) syncLoadingIds.add(credentialId)
                                              else syncLoadingIds.remove(credentialId)
            HomeViewModel.ButtonType.SHARE -> if (isLoading) shareLoadingIds.add(credentialId)
                                              else shareLoadingIds.remove(credentialId)
        }
        // Find the position of this item and notify only that viewholder
        val position = currentList.indexOfFirst { it.id == credentialId }
        if (position != -1) notifyItemChanged(position, buttonType /* payload */)
    }

    // ── Adapter overrides ────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CredentialViewHolder {
        val binding = ItemCredentialBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CredentialViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CredentialViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Partial-bind with payload — only refreshes button states when called
     * from [setButtonLoading], avoiding a full rebind flash.
     */
    override fun onBindViewHolder(
        holder: CredentialViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.bindButtonStates(getItem(position))
        }
    }

    // ── ViewHolder ───────────────────────────────────────────────────────

    inner class CredentialViewHolder(
        private val binding: ItemCredentialBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        init {
            val ctx = binding.root.context
            // Synced chip
            binding.chipSynced.text = ctx.getString(R.string.status_synced)
            binding.chipSynced.chipBackgroundColor = ctx.getColorStateList(R.color.vault_status_synced_bg)
            binding.chipSynced.setTextColor(ctx.getColor(R.color.vault_secondary))
            binding.chipSynced.chipIcon = ctx.getDrawable(R.drawable.ic_check)
            binding.chipSynced.chipIconTint = null
            binding.chipSynced.isChipIconVisible = true

            // Shared chip
            binding.chipShared.text = ctx.getString(R.string.status_shared)
            binding.chipShared.chipBackgroundColor = ctx.getColorStateList(R.color.vault_status_shared_bg)
            binding.chipShared.setTextColor(ctx.getColor(R.color.vault_tertiary))
            binding.chipShared.chipIcon = ctx.getDrawable(R.drawable.ic_shared)
            binding.chipShared.chipIconTint = ctx.getColorStateList(R.color.vault_tertiary_fixed_dim)
            binding.chipShared.isChipIconVisible = true

            // Offline chip
            binding.chipOffline.text = ctx.getString(R.string.status_offline)
            binding.chipOffline.chipBackgroundColor = ctx.getColorStateList(R.color.vault_status_offline_bg)
            binding.chipOffline.setTextColor(ctx.getColor(R.color.vault_on_surface_variant))
            binding.chipOffline.chipIcon = ctx.getDrawable(R.drawable.ic_offline)
            binding.chipOffline.chipIconTint = ctx.getColorStateList(R.color.vault_outline)
            binding.chipOffline.isChipIconVisible = true
        }

        fun bind(credential: Credential) {
            binding.tvCredentialTitle.text = credential.title

            if (credential.lastSyncedAt != null) {
                binding.tvLastSynced.text = "Last synced: " + dateFormat.format(Date(credential.lastSyncedAt))
                binding.tvLastSynced.isVisible = true
            } else {
                binding.tvLastSynced.isVisible = false
            }

            bindChips(credential)
            bindButtonStates(credential)

            binding.root.setOnClickListener { onCardClick(credential) }
            binding.btnSync.setOnClickListener { onSyncClick(credential) }
            binding.btnShare.setOnClickListener { onShareClick(credential) }
        }

        private fun bindChips(credential: Credential) {
            val isSynced = credential.isSynced
            val isShared = credential.isShared
            val isOffline = !isSynced && !isShared

            binding.chipSynced.isVisible = isSynced
            binding.chipShared.isVisible = isShared
            binding.chipOffline.isVisible = isOffline
        }

        /** Refreshes only the Sync/Share button enabled/loading states. */
        fun bindButtonStates(credential: Credential) {
            val ctx = binding.root.context
            val isSyncing = syncLoadingIds.contains(credential.id)

            if (credential.isReceived) {
                binding.btnShare.isVisible = false
                binding.btnSync.isVisible = true
                binding.btnSync.isEnabled = !isSyncing
                binding.btnSync.text = if (isSyncing) ctx.getString(R.string.btn_refreshing) else ctx.getString(R.string.btn_refresh)
                binding.btnSync.icon = if (!isSyncing) ctx.getDrawable(R.drawable.ic_sync) else null
                return
            }
            binding.btnSync.isVisible = true
            binding.btnShare.isVisible = true

            val isSharing  = shareLoadingIds.contains(credential.id)

            binding.btnSync.isEnabled  = !isSyncing
            binding.btnShare.isEnabled = !isSharing

            // Show loading text while in progress
            binding.btnSync.text  = if (isSyncing)  "Syncing…" else "Sync"
            binding.btnShare.text = if (isSharing) "Sharing…" else "Share"

            // Icon visibility: hide while loading to make room for text
            binding.btnSync.icon  = if (!isSyncing)
                binding.root.context.getDrawable(R.drawable.ic_sync) else null
            binding.btnShare.icon = if (!isSharing)
                binding.root.context.getDrawable(R.drawable.ic_share) else null
        }
    }

    // ── DiffUtil ─────────────────────────────────────────────────────────

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Credential>() {
            override fun areItemsTheSame(old: Credential, new: Credential) = old.id == new.id
            override fun areContentsTheSame(old: Credential, new: Credential) = old == new
        }
    }
}
