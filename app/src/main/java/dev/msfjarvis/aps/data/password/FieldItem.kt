package dev.msfjarvis.aps.data.password

class FieldItem(val key: String, val value: String, val action: ActionType) {

  enum class ActionType {
    ACTION_COPY, ACTION_HIDE
  }
}