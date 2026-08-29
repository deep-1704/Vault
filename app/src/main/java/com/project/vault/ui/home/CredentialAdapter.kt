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

        fun bind(credential: Credential) {
            binding.tvCredentialTitle.text = credential.title
            applyStatusChip(binding.chipStatus, credential.status)
            bindButtonStates(credential)

            binding.root.setOnClickListener { onCardClick(credential) }
            binding.btnSync.setOnClickListener { onSyncClick(credential) }
            binding.btnShare.setOnClickListener { onShareClick(credential) }
        }

        /** Refreshes only the Sync/Share button enabled/loading states. */
        fun bindButtonStates(credential: Credential) {
            val isSyncing  = syncLoadingIds.contains(credential.id)
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

        private fun applyStatusChip(chip: Chip, status: CredentialStatus) {
            val ctx = chip.context
            when (status) {
                CredentialStatus.SYNCED -> {
                    chip.text = "Synced"
                    chip.chipBackgroundColor =
                        ctx.getColorStateList(R.color.vault_status_synced_bg)
                    chip.setTextColor(ctx.getColor(R.color.vault_secondary))
                    chip.chipIcon = ctx.getDrawable(R.drawable.ic_check)
                    chip.chipIconTint = null
                    chip.isChipIconVisible = true
                }
                CredentialStatus.SHARED -> {
                    chip.text = "Shared"
                    chip.chipBackgroundColor =
                        ctx.getColorStateList(R.color.vault_status_shared_bg)
                    chip.setTextColor(ctx.getColor(R.color.vault_tertiary))
                    chip.chipIcon = ctx.getDrawable(R.drawable.ic_shared)
                    chip.chipIconTint = ctx.getColorStateList(R.color.vault_tertiary_fixed_dim)
                    chip.isChipIconVisible = true
                }
                CredentialStatus.OFFLINE -> {
                    chip.text = "Offline"
                    chip.chipBackgroundColor =
                        ctx.getColorStateList(R.color.vault_status_offline_bg)
                    chip.setTextColor(ctx.getColor(R.color.vault_on_surface_variant))
                    chip.chipIcon = ctx.getDrawable(R.drawable.ic_offline)
                    chip.chipIconTint = ctx.getColorStateList(R.color.vault_outline)
                    chip.isChipIconVisible = true
                }
            }
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
