package org.example.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.network.AuthRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen() {
    // Üzemmód: Bejelentkezés vagy Regisztráció?
    var isRegisterMode by remember { mutableStateOf(false) }

    // Alap mezők
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Extra regisztrációs mezők
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }

    // Legördülő lista állapotai a Nem kiválasztásához
    var genderExpanded by remember { mutableStateOf(false) }
    val genderOptions = listOf("Férfi", "Nő", "Egyéb / Nem kívánom megadni")
    var gender by remember { mutableStateOf(genderOptions[0]) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isRegisterMode) "Regisztráció" else "Üdvözlünk!",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail cím") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Jelszó") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            // Jelszó információ megjelenítése
            supportingText = {
                if (isRegisterMode) {
                    Text("Minimum 6 karakter. (Betűk és számok is megengedettek)")
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Csak regisztráció esetén látszik
        if (isRegisterMode) {

            // ÉLETKOR (Csak egész számok)
            OutlinedTextField(
                value = age,
                onValueChange = { newValue ->
                    // Csak akkor engedjük átírni, ha kizárólag számjegyeket tartalmaz
                    if (newValue.all { it.isDigit() }) {
                        age = newValue
                    }
                },
                label = { Text("Életkor (év)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // NEM (Legördülő lista)
            ExposedDropdownMenuBox(
                expanded = genderExpanded,
                onExpandedChange = { genderExpanded = !genderExpanded }
            ) {
                OutlinedTextField(
                    value = gender,
                    onValueChange = {},
                    readOnly = true, // Nem írhat bele kézzel
                    label = { Text("Nem") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = genderExpanded,
                    onDismissRequest = { genderExpanded = false }
                ) {
                    genderOptions.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                gender = selectionOption
                                genderExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // MAGASSÁG (Számok és egyetlen tizedesjel engedélyezése)
            OutlinedTextField(
                value = height,
                onValueChange = { newValue ->
                    if (newValue.matches(Regex("^\\d*[.,]?\\d*$"))) {
                        height = newValue
                    }
                },
                label = { Text("Magasság (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // TESTSÚLY (Számok és egyetlen tizedesjel engedélyezése)
            OutlinedTextField(
                value = weight,
                onValueChange = { newValue ->
                    if (newValue.matches(Regex("^\\d*[.,]?\\d*$"))) {
                        weight = newValue
                    }
                },
                label = { Text("Testsúly (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        if (isRegisterMode) {
                            // REGISZTRÁCIÓ
                            val result = AuthRepository.registerUser(
                                emailStr = email,
                                passwordStr = password,
                                age = age.toIntOrNull(),
                                gender = gender, // Itt már a legördülőből választott String megy
                                height_cm = height.replace(",", ".").toDoubleOrNull(), // Vesszőt pontra cseréljük a konverzióhoz
                                weight_kg = weight.replace(",", ".").toDoubleOrNull()
                            )
                            if (result.isFailure) {
                                errorMessage = result.exceptionOrNull()?.message ?: "Ismeretlen hiba a regisztráció során!"
                            }
                        } else {
                            // BEJELENTKEZÉS
                            val result = AuthRepository.loginUser(email, password)
                            if (result.isFailure) {
                                errorMessage = "Hibás e-mail vagy jelszó!"
                            }
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRegisterMode) "Fiók létrehozása" else "Bejelentkezés")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Üzemmód váltó gomb
            TextButton(
                onClick = {
                    isRegisterMode = !isRegisterMode
                    errorMessage = null
                }
            ) {
                Text(if (isRegisterMode) "Már van fiókom. Bejelentkezés" else "Nincs még fiókom. Regisztráció")
            }
        }
    }
}