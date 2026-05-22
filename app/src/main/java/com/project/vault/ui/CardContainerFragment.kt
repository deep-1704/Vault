package com.project.vault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.vault.R
import com.project.vault.databinding.CardContainerFragmentBinding
import com.project.vault.databinding.CdCardBinding
import com.project.vault.entity.CDCard
import com.project.vault.ui.viewModel.CardContainerViewModel

class CLAdapter(
    val cards: List<CDCard>,
    val onCardDeleteBtnClickListener: (Int) -> Unit
): RecyclerView.Adapter<CLAdapter.CardViewHolder>(){

    class CardViewHolder(val binding: CdCardBinding): RecyclerView.ViewHolder(binding.root)

    // Generates the view (without data)
    override fun onCreateViewHolder(
        parent: ViewGroup,
        p1: Int
    ): CardViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding: CdCardBinding = CdCardBinding.inflate(inflater, parent, false)

        return CardViewHolder(binding)
    }


    override fun onBindViewHolder(cardViewHolder: CardViewHolder, position: Int) {
        val card = cards[position]
        cardViewHolder.binding.apply {
            tvCardName.text = card.cardName
            tvCardNum.text = card.cardNumber
            tvCardHolderName.text = card.cardHolderName
            tvExpDate.text = "${card.expMonth}/${card.expYear}"
            tvCvv.text = card.cvv.toString()
        }

        cardViewHolder.binding.cardDeleteBtn.setOnClickListener {
            onCardDeleteBtnClickListener(position)
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
                binding.cardContainerRv.adapter = CLAdapter(cards) { pos: Int ->
                    viewModel.deleteCardAtIndex(pos)
                }
            }
        }

        return binding.root
    }
}