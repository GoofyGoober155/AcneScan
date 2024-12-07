package com.dicoding.acnescan.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dicoding.acnescan.databinding.ActivityMainBinding
import com.dicoding.acnescan.ui.BottomNavigation
import com.dicoding.acnescan.ui.RegisterActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Menghubungkan binding dengan layout activity_main
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set onClickListener untuk button login tanpa akun
        binding.btnLoginWithoutAccount.setOnClickListener {
            val intent = Intent(this, BottomNavigation::class.java)
            startActivity(intent)
            finish() // Finish MainActivity agar tidak kembali lagi ketika menekan back button
        }

        // Set onClickListener untuk button menuju halaman LoginActivity
        binding.btnLoginWithEmail.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // Clear back stack dari LoginActivity
            startActivity(intent)
        }

        binding.tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // Clear back stack dari LoginActivity
            startActivity(intent)
        }
    }
}