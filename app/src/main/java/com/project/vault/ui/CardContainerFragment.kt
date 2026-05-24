package com.project.vault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.project.vault.R
import com.project.vault.databinding.CardContainerFragmentBinding
import com.project.vault.databinding.CdCardBinding
import com.project.vault.databinding.EncCdCardBinding
import com.project.vault.entity.CDCard
import com.project.vault.entity.EncCDCard
import com.project.vault.entity.ICDCard
import com.project.vault.ui.viewModel.CardContainerViewModel

import com.project.vault.util.BiometricHelper

class CLAdapter(
    val cards: List<ICDCard>,
    val onCardDeleteBtnClickListener: (Int) -> Unit,
    val onCardUnlockBtnClickListener: (Int) -> Unit
): RecyclerView.Adapter<CLAdapter.CardViewHolder>(){

    companion object {
        private const val TYPE_CD_CARD = 0
        private const val TYPE_ENC_CD_CARD = 1
    }

    class CardViewHolder(val binding: ViewBinding): RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return if (cards[position] is CDCard) TYPE_CD_CARD else TYPE_ENC_CD_CARD
    }

    // Generates the view (without data)
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CardViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if(viewType == TYPE_CD_CARD){
            CdCardBinding.inflate(inflater, parent, false)
        } else {
            EncCdCardBinding.inflate(inflater, parent, false)
        }

        return CardViewHolder(binding)
    }


    override fun onBindViewHolder(cardViewHolder: CardViewHolder, position: Int) {
        val card = cards[position]
        when (val binding = cardViewHolder.binding) {
            is CdCardBinding -> {
                (card as? CDCard)?.apply {
                    binding.tvCardName.text = cardName
                    binding.tvCardNum.text = cardNumber
                    binding.tvCardHolderName.text = cardHolderName
                    binding.tvExpDate.text = "$expMonth/$expYear"
                    binding.tvCvv.text = cvv.toString()

                    binding.cardDeleteBtn.setOnClickListener {
                        onCardDeleteBtnClickListener(position)
                    }
                }
            }
            is EncCdCardBinding -> {
                (card as? EncCDCard)?.apply {
                    binding.tvEncCardName.text = cardName

                    binding.cardUnlockBtn.setOnClickListener {
                        onCardUnlockBtnClickListener(position)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = cards.size
}
class CardContainerFragment: Fragment(R.layout.card_container_fragment) {
    private lateinit var binding: CardContainerFragmentBinding
    private lateinit var viewModel: CardContainerViewModel


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CardContainerFragmentBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this).get(CardContainerViewModel::class.java)

        binding.cardContainerRv.layoutManager = LinearLayoutManager(container?.context)

        viewModel.cards.observe(viewLifecycleOwner) { cards ->
            binding.cardContainerRv.visibility = View.GONE
            binding.noCardsTv.visibility = View.GONE

            if(cards.isEmpty()){
                binding.noCardsTv.visibility = View.VISIBLE
            } else {
                binding.cardContainerRv.visibility = View.VISIBLE
                binding.cardContainerRv.adapter = CLAdapter(
                    cards = cards,
                    onCardDeleteBtnClickListener = { pos: Int ->
                        viewModel.deleteCardAtIndex(pos)
                    },
                    onCardUnlockBtnClickListener = { pos: Int ->
                        viewModel.unlockCardAtIndex(pos) {
                            BiometricHelper.authenticate(
                                requireActivity(),
                                "Unlock Card",
                                "Authenticate to view card details"
                            )
                        }
                    }
                )
            }
        }

        return binding.root
    }
}