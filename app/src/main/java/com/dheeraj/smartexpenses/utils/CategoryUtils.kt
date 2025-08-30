package com.dheeraj.smartexpenses.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dheeraj.smartexpenses.ui.theme.*

object CategoryUtils {
    
    fun getIconFromName(iconName: String): ImageVector {
        return when (iconName) {
            "Restaurant" -> Icons.Outlined.Restaurant
            "DirectionsCar" -> Icons.Outlined.DirectionsCar
            "ShoppingCart" -> Icons.Outlined.ShoppingCart
            "Movie" -> Icons.Outlined.Movie
            "Receipt" -> Icons.Outlined.Receipt
            "LocalHospital" -> Icons.Outlined.LocalHospital
            "School" -> Icons.Outlined.School
            "AccountBalance" -> Icons.Outlined.AccountBalance
            "Home" -> Icons.Outlined.Home
            "Work" -> Icons.Outlined.Work
            "SportsSoccer" -> Icons.Outlined.SportsSoccer
            "MusicNote" -> Icons.Outlined.MusicNote
            "Flight" -> Icons.Outlined.Flight
            "CardGiftcard" -> Icons.Outlined.CardGiftcard
            "Coffee" -> Icons.Outlined.Coffee
            "FitnessCenter" -> Icons.Outlined.FitnessCenter
            "Book" -> Icons.Outlined.Book
            "Phone" -> Icons.Outlined.Phone
            "Wifi" -> Icons.Outlined.Wifi
            "ElectricBolt" -> Icons.Outlined.ElectricBolt
            "WaterDrop" -> Icons.Outlined.WaterDrop
            "LocalGasStation" -> Icons.Outlined.LocalGasStation
            "LocalParking" -> Icons.Outlined.LocalParking
            "LocalTaxi" -> Icons.Outlined.LocalTaxi
            "DirectionsBus" -> Icons.Outlined.DirectionsBus
            "Train" -> Icons.Outlined.Train
            "DirectionsBike" -> Icons.Outlined.DirectionsBike
            "DirectionsWalk" -> Icons.Outlined.DirectionsWalk
            "Add" -> Icons.Outlined.Add
            else -> Icons.Outlined.AccountBalance
        }
    }
    
    fun getColorFromString(colorString: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(colorString))
        } catch (e: Exception) {
            // Fallback to default colors based on common category names
            when {
                colorString.contains("Food") -> CategoryFood
                colorString.contains("Transport") -> CategoryTransport
                colorString.contains("Shopping") -> CategoryShopping
                colorString.contains("Entertainment") -> CategoryEntertainment
                colorString.contains("Bills") -> CategoryBills
                colorString.contains("Health") -> CategoryHealth
                colorString.contains("Education") -> CategoryEducation
                else -> CategoryOther
            }
        }
    }
    
    fun getDefaultCategoryColor(categoryName: String): Color {
        return when (categoryName.lowercase()) {
            "food" -> CategoryFood
            "transport" -> CategoryTransport
            "shopping" -> CategoryShopping
            "entertainment" -> CategoryEntertainment
            "bills" -> CategoryBills
            "health" -> CategoryHealth
            "education" -> CategoryEducation
            else -> CategoryOther
        }
    }
    
    fun getDefaultCategoryIcon(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "food" -> "Restaurant"
            "transport" -> "DirectionsCar"
            "shopping" -> "ShoppingCart"
            "entertainment" -> "Movie"
            "bills" -> "Receipt"
            "health" -> "LocalHospital"
            "education" -> "School"
            else -> "AccountBalance"
        }
    }
}
