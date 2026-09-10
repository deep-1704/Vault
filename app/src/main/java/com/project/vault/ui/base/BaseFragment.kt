package com.project.vault.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * Abstract base fragment that standardises ViewBinding lifecycle management
 * and provides common UI helpers.
 *
 * Subclasses must:
 *  1. Specify the ViewBinding type as the generic parameter [VB].
 *  2. Implement [getViewBinding] to inflate the binding.
 *
 * The binding is automatically cleared in [onDestroyView] to prevent memory leaks.
 *
 * Example subclass:
 *
 *   class HomeFragment : BaseFragment<FragmentHomeBinding>() {
 *       override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
 *           FragmentHomeBinding.inflate(inflater, container, false)
 *
 *       override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *           super.onViewCreated(view, savedInstanceState)
 *           // Use `binding.someView` safely here
 *       }
 *   }
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null

    /** Non-null binding reference, valid between [onViewCreated] and [onDestroyView]. */
    protected val binding: VB
        get() = _binding ?: error("Binding accessed outside of view lifecycle.")

    /**
     * Inflate and return the ViewBinding for this fragment.
     * Called once in [onCreateView].
     */
    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = getViewBinding(inflater, container)
        return binding.root
    }

    /**
     * Override this in subclasses to observe the ViewModel's error stream.
     * Automatically wires [BaseViewModel.errorLiveData] to [showToast].
     */
    protected fun observeErrors(viewModel: BaseViewModel) {
        viewModel.errorLiveData.observe(viewLifecycleOwner) { message ->
            showToast(message)
        }
    }

    /**
     * Shows a short Toast with [message]. Safe to call from any lifecycle callback
     * after [onAttach].
     */
    protected fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
