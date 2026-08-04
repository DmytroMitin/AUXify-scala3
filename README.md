# AUXify-scala3

Re-implementing/re-launching https://github.com/DmytroMitin/AUXify for Scala 3 using:

- quasiquotes for Scala 3:

  https://github.com/DmytroMitin/quasiquotes-scala3 

  https://github.com/DmytroMitin/quasiquotes-scala3-control

- macro-paradise compiler plugin for Scala 3:

  https://github.com/DmytroMitin/macroparadise-scala3

when there is enough support from quasiquotes and macro-paradise.

Goals:

- firstly `@apply`

- secondly `@aux`

- thirdly `@self`, `@instance`, `@delegated`, `@syntax`, `@poly`, maybe more...