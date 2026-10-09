package com.archsolutions.shopmanager.app

// DayGate represents the app startup gate evaluated in Milestone 3
sealed interface DayGateState {
    data object OpenDayRequired : DayGateState
    data object PreviousDayStillOpen : DayGateState
    data object TodayClosed : DayGateState
    data object Ready : DayGateState
}
