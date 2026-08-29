package com.project.vault.ui.placeholder

import android.view.LayoutInflater
import android.view.ViewGroup
import com.project.vault.databinding.FragmentPlaceholderBinding
import com.project.vault.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Placeholder fragment used as the start destination of the navigation graph.
 *
 * Replace this fragment with the real first screen once the app's feature
 * set is defined. Update [res/navigation/nav_graph.xml] accordingly.
 */
@AndroidEntryPoint
class PlaceholderFragment : BaseFragment<FragmentPlaceholderBinding>() {

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentPlaceholderBinding {
        return FragmentPlaceholderBinding.inflate(inflater, container, false)
    }
}
