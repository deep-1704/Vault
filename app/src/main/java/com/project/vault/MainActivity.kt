package com.project.vault

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.project.vault.databinding.ActivityMainBinding
import com.project.vault.ui.CardContainerFragment
import com.project.vault.ui.NewCardFormFragment
import com.project.vault.util.MainActivityFragmentState

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val fragmentStateObserver = Observer<MainActivityFragmentState>{newState ->
        setFragment(newState)
        setNavButton(newState)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Keeps system bars visible over your app
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // Set padding equal to the system bar dimensions to avoid overlap of system bars
        // with app contents
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom)
            )
            insets
        }

        // Add card-list fragment when the activity is created for the first time
        if(savedInstanceState == null){
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<CardContainerFragment>(containerViewId = binding.fragmentContainer.id)
            }
        }

        viewModel.fragmentState.observe(this, fragmentStateObserver)
    }

    private fun setFragment(state: MainActivityFragmentState) {
        val currentFragment = supportFragmentManager.findFragmentById(binding.fragmentContainer.id)
        when(state) {
            MainActivityFragmentState.CARD_LIST -> {
                if (currentFragment !is CardContainerFragment) {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<CardContainerFragment>(containerViewId = binding.fragmentContainer.id)
                    }
                }
            }
            MainActivityFragmentState.NEW_CARD_FORM -> {
                if (currentFragment !is NewCardFormFragment) {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<NewCardFormFragment>(containerViewId = binding.fragmentContainer.id)
                    }
                }
            }
        }
    }

    private fun setNavButton(state: MainActivityFragmentState){
        val navButton = binding.navBarBtn
        when(state) {
            MainActivityFragmentState.CARD_LIST -> {
                navButton.setText(R.string.addCard_btn_text)
                navButton.setOnClickListener {
                    viewModel.fragmentState.value = MainActivityFragmentState.NEW_CARD_FORM
                }
            }
            MainActivityFragmentState.NEW_CARD_FORM -> {
                navButton.setText(R.string.back_button_text)
                navButton.setOnClickListener {
                    viewModel.fragmentState.value = MainActivityFragmentState.CARD_LIST
                }
            }
        }
    }
}