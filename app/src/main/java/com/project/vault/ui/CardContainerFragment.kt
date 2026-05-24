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

class CLAdapter(
    val cards: List<ICDCard>,
    val onCardDeleteBtnClickListener: (Int) -> Unit,
    val onCardUnlockBtnClickListener: (Int) -> Unit
): RecyclerView.Adapter<CLAdapter.CardViewHolder>(){

    class CardViewHolder(val binding: ViewBinding): RecyclerView.ViewHolder(binding.root)

    // Generates the view (without data)
    override fun onCreateViewHolder(
        parent: ViewGroup,
        pos: Int
    ): CardViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if(cards[pos] is CDCard){
            CdCardBinding.inflate(inflater, parent, false)
        } else {
            EncCdCardBinding.inflate(inflater, parent, false)
        }

        return CardViewHolder(binding)
    }


    override fun onBindViewHolder(cardViewHolder: CardViewHolder, position: Int) {
        when (val binding = cardViewHolder.binding) {
            is CdCardBinding -> {
                val card = cards[position] as CDCard
                binding.apply {
                    tvCardName.text = card.cardName
                    tvCardNum.text = card.cardNumber
                    tvCardHolderName.text = card.cardHolderName
                    tvExpDate.text = "${card.expMonth}/${card.expYear}"
                    tvCvv.text = card.cvv.toString()

                    cardDeleteBtn.setOnClickListener {
                        onCardDeleteBtnClickListener(position)
                    }
                }
            }
            is EncCdCardBinding -> {
                val card = cards[position] as EncCDCard
                binding.apply {
                    tvEncCardName.text = card.cardName

                    cardUnlockBtn.setOnClickListener {
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
                        viewModel.unlockCardAtIndex(pos)
                    }
                )
            }
        }

        return binding.root
    }
}