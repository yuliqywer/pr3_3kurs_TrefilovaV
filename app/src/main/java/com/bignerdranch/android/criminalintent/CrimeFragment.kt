package com.bignerdranch.android.criminalintent

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlin.concurrent.thread

private const val REQUEST_DATE = 0
private const val REQUEST_CONTACT = 1
private const val DATE_FORMAT = "EEE, MMM, dd"
private const val REQUEST_PHONE = 2
private const val REQUEST_CONTACTS_PERMISSION = 100

class CrimeFragment : Fragment() {
    private lateinit var crime: Crime
    private lateinit var titleField: EditText
    private lateinit var dateButton: Button
    private lateinit var reportButton: Button
    private lateinit var suspectButton: Button
    private lateinit var callSuspectButton: Button
    private lateinit var solvedCheckBox: CheckBox
    private var isFormValid = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        crime = Crime()
        thread {
            CrimeRepository.get().addCrime(crime)
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_crime, container, false)
        titleField = view.findViewById(R.id.crime_title) as EditText
        dateButton = view.findViewById(R.id.crime_date) as Button
        reportButton = view.findViewById(R.id.crime_report) as Button
        suspectButton = view.findViewById(R.id.crime_suspect) as Button
        callSuspectButton = view.findViewById(R.id.crime_call_suspect) as Button
        solvedCheckBox = view.findViewById(R.id.crime_solved) as CheckBox
        dateButton.apply {
            text = crime.date.toString()
            isEnabled = false
        }
        reportButton.text = getString(R.string.crime_report_text)
        updateUI()

        return view
    }
    override fun onStart() {
        super.onStart()
        val titleWatcher = object : TextWatcher {
            override fun beforeTextChanged(
                sequence: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }
            override fun onTextChanged(
                sequence: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                crime.title = sequence.toString()
                updateButtonState()
            }
            override fun afterTextChanged(sequence: Editable?) {
            }

        }
        titleField.addTextChangedListener(titleWatcher)

        solvedCheckBox.setOnCheckedChangeListener { _, isChecked ->
            crime.isSolved = isChecked
        updateButtonState()
        }
        reportButton.setOnClickListener {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getCrimeReport())
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    getString(R.string.crime_report_subject))
            }.also { intent ->
                val chooserIntent =
                    Intent.createChooser(
                        intent,
                        getString(R.string.send_report))
                startActivity(chooserIntent)
            }
        }
        suspectButton.apply {
            val pickContactIntent = Intent(
                Intent.ACTION_PICK,
                ContactsContract.Contacts.CONTENT_URI
            )
            val pm: PackageManager = requireActivity().packageManager
            val resolved = pm.resolveActivity(
                pickContactIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            if (resolved == null) {
                isEnabled = false
            } else {
                setOnClickListener {
                    startActivityForResult(pickContactIntent, REQUEST_CONTACT)
                }
            }
        }
        callSuspectButton.setOnClickListener {
            if (crime.suspectPhoneId.isBlank()) {
                Toast.makeText(
                    context,
                    getString(R.string.crime_call_no_number),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val permission = android.Manifest.permission.READ_CONTACTS
            if (ContextCompat.checkSelfPermission(
                    requireContext(), permission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                dialSuspect(crime.suspectPhoneId)
            } else {
                requestPermissions(arrayOf(permission), REQUEST_CONTACTS_PERMISSION)
            }
        }

    }

    private fun updateButtonState() {
        dateButton.isEnabled = crime.title.isNotEmpty() && crime.isSolved
    }
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when {
            resultCode != Activity.RESULT_OK -> return

            requestCode == REQUEST_CONTACT && data != null -> {
                val contactUri = data.data ?: return

                val queryFields = arrayOf(
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts._ID
                )

                val cursor = requireActivity().contentResolver.query(
                    contactUri,
                    queryFields,
                    null, null, null
                )

                cursor?.use {
                    if (it.count == 0) return
                    it.moveToFirst()
                    val suspect = it.getString(0)

                    crime.suspect = suspect

                    thread {
                        CrimeRepository.get().updateCrime(crime)
                    }

                    updateUI()
                }
            }
        }
    }
    private fun updateUI() {
        if (crime.suspect.isNotBlank()) {
            suspectButton.text = crime.suspect
        } else {
            suspectButton.text = getString(R.string.crime_suspect_text)
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CONTACTS_PERMISSION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                if (crime.suspectPhoneId.isNotBlank()) {
                    dialSuspect(crime.suspectPhoneId)
                }
            } else {
                Toast.makeText(
                    context,
                    getString(R.string.crime_call_no_permission),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    private fun getCrimeReport(): String {
        val solvedString = if (crime.isSolved) {
            getString(R.string.crime_report_solved)
        } else {
            getString(R.string.crime_report_unsolved)
        }

        val dateString =
            DateFormat.format(DATE_FORMAT, crime.date).toString()

        val suspect = if (crime.suspect.isBlank()) {
            getString(R.string.crime_report_no_suspect)
        } else {
            getString(R.string.crime_report_suspect, crime.suspect)
        }

        return getString(
            R.string.crime_report,
            crime.title,
            dateString,
            solvedString,
            suspect
        )
    }
    private fun dialSuspect(contactId: String) {
        val phoneQueryFields = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val whereClause =
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val whereArgs = arrayOf(contactId)

        val phoneCursor = requireActivity().contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            phoneQueryFields,
            whereClause,
            whereArgs,
            null
        )

        phoneCursor?.use {
            if (it.count == 0) {
                Toast.makeText(
                    context,
                    getString(R.string.crime_call_no_number),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            it.moveToFirst()
            val phoneNumber = it.getString(0)

            val phoneUri = Uri.parse("tel:$phoneNumber")

            val dialIntent = Intent(Intent.ACTION_DIAL, phoneUri)
            startActivity(dialIntent)
        }
    }
}