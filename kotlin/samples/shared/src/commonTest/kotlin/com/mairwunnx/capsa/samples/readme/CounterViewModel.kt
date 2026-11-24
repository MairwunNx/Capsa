// From the README of Capsa, word for word: https://github.com/MairwunNx/Capsa
// When this stops compiling or passing, the README needs the same change.
package com.mairwunnx.capsa.samples.readme

import com.mairwunnx.capsa.viewmodel.CapsaViewModel

class CounterViewModel : CapsaViewModel() { val state = install(counter()) }
