package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dheeraj.smartexpenses.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onCategoryAdded: (name: String, icon: String, color: String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("Add") }
    var selectedColor by remember { mutableStateOf(CategoryOther) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    val availableIcons = remember {
        listOf(
            "Add" to Icons.Outlined.Add,
            "Restaurant" to Icons.Outlined.Restaurant,
            "DirectionsCar" to Icons.Outlined.DirectionsCar,
            "ShoppingCart" to Icons.Outlined.ShoppingCart,
            "Movie" to Icons.Outlined.Movie,
            "Receipt" to Icons.Outlined.Receipt,
            "LocalHospital" to Icons.Outlined.LocalHospital,
            "School" to Icons.Outlined.School,
            "AccountBalance" to Icons.Outlined.AccountBalance,
            "Home" to Icons.Outlined.Home,
            "Work" to Icons.Outlined.Work,
            "Sports" to Icons.Outlined.SportsSoccer,
            "Music" to Icons.Outlined.MusicNote,
            "Travel" to Icons.Outlined.Flight,
            "Gift" to Icons.Outlined.CardGiftcard,
            "Coffee" to Icons.Outlined.Coffee,
            "Fitness" to Icons.Outlined.FitnessCenter,
            "Book" to Icons.Outlined.Book,
            "Phone" to Icons.Outlined.Phone,
            "Wifi" to Icons.Outlined.Wifi,
            "Electric" to Icons.Outlined.ElectricBolt,
            "Water" to Icons.Outlined.WaterDrop,
            "Gas" to Icons.Outlined.LocalGasStation,
            "Parking" to Icons.Outlined.LocalParking,
            "Taxi" to Icons.Outlined.LocalTaxi,
            "Bus" to Icons.Outlined.DirectionsBus,
            "Train" to Icons.Outlined.Train,
            "Bike" to Icons.Outlined.DirectionsBike,
            "Walk" to Icons.Outlined.DirectionsWalk
        )
    }
    
    val availableColors = remember {
        listOf(
            CategoryFood, CategoryTransport, CategoryShopping, CategoryEntertainment,
            CategoryBills, CategoryHealth, CategoryEducation, CategoryOther,
            Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5),
            Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50),
            Color(0xFF8BC34A), Color(0xFFCDDC39), Color(0xFFFFEB3B), Color(0xFFFFC107),
            Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFF795548), Color(0xFF9E9E9E)
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add New Category",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category Name Input
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                
                // Icon Selection
                Column {
                    Text(
                        "Select Icon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current selected icon
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(selectedColor.copy(alpha = 0.1f))
                                .border(2.dp, selectedColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = availableIcons.find { it.first == selectedIcon }?.second ?: Icons.Outlined.Add
                            Icon(
                                imageVector = icon,
                                contentDescription = selectedIcon,
                                tint = selectedColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Icon picker button
                        OutlinedButton(
                            onClick = { showIconPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Choose Icon")
                        }
                    }
                }
                
                // Color Selection
                Column {
                    Text(
                        "Select Color",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(80.dp)
                    ) {
                        items(availableColors) { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selectedColor == color) 3.dp else 1.dp,
                                        color = if (selectedColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = color }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onCategoryAdded(categoryName, selectedIcon, selectedColor.toString())
                        onDismiss()
                    }
                },
                enabled = categoryName.isNotBlank()
            ) {
                Text("Add Category")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // Icon Picker Dialog
    if (showIconPicker) {
        AlertDialog(
            onDismissRequest = { showIconPicker = false },
            title = {
                Text("Select Icon", fontWeight = FontWeight.Bold)
            },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(availableIcons) { (iconName, icon) ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(selectedColor.copy(alpha = 0.1f))
                                .border(
                                    width = if (selectedIcon == iconName) 2.dp else 1.dp,
                                    color = if (selectedIcon == iconName) selectedColor else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedIcon = iconName
                                    showIconPicker = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = iconName,
                                tint = selectedColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
