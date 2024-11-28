package com.dicoding.acnescan.ui.history

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.dicoding.acnescan.adapter.HistoryAdapter
import com.dicoding.acnescan.databinding.FragmentHistoryBinding
import com.dicoding.acnescan.ui.camera.AnalysisActivity

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val historyViewModel: HistoryViewModel by viewModels()
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(false)

        historyAdapter = HistoryAdapter { event ->
            val intent = Intent(requireContext(), AnalysisActivity::class.java).apply {
                putExtra(AnalysisActivity.EXTRA_IMAGE_PATH, event.imagePath)
            }
            startActivity(intent)
        }

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.adapter = historyAdapter

        // Observe history data
        historyViewModel.history.observe(viewLifecycleOwner) { historyList ->
            if (historyList.isNullOrEmpty()) {
                binding.historyRecyclerView.visibility = View.GONE
                binding.placeholderText.visibility = View.VISIBLE
            } else {
                binding.historyRecyclerView.visibility = View.VISIBLE
                binding.placeholderText.visibility = View.GONE
                historyAdapter.submitList(historyList)
            }
        }

        // Tombol Hapus Semua
        binding.buttonDeleteAll.setOnClickListener {
            historyViewModel.deleteAllHistory()
            Toast.makeText(requireContext(), "Semua data history telah dihapus.", Toast.LENGTH_SHORT).show()
        }

        // Tombol Refresh
        binding.buttonRefresh.setOnClickListener {
            historyViewModel.refreshHistory()
            Toast.makeText(requireContext(), "Data history diperbarui.", Toast.LENGTH_SHORT).show()
        }

        // Observe error messages
        historyViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }
}