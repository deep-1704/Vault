package com.project.vault

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.project.vault.util.MainActivityFragmentState

class MainViewModel: ViewModel() {
    val fragmentState: MutableLiveData<MainActivityFragmentState> by lazy {
        MutableLiveData<MainActivityFragmentState>(MainActivityFragmentState.CARD_LIST)
    }
}