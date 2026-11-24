package com.mairwunnx.capsa.viewmodel

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.SliceSaver
import com.mairwunnx.capsa.capability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks [CapsaViewModel.installSaved] on Android, where the snapshot is a real [Bundle].
 *
 * Host tests can’t: the stub android.jar throws on every [Bundle] call. Here the snapshot travels the way the system moves it
 * when the process dies: the handle saves a [Bundle], a [Parcel] turns it into bytes and back, and a new handle restores it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SavedStateOnDeviceTest {

    private data class Form(val name: String = "", val age: Int = 0, val loaded: Boolean = false)
    private sealed interface FormIntent : Intent
    private data class NameTyped(val name: String) : FormIntent
    private data class AgeTyped(val age: Int) : FormIntent

    private data class FormSaved(val name: String, val age: Int)

    /** Written by hand, so the tests don’t need the serialization compiler plugin. */
    private object FormSavedSerializer : KSerializer<FormSaved> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FormSaved") {
            element<String>("name")
            element<Int>("age")
        }

        override fun serialize(encoder: Encoder, value: FormSaved) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.name)
                encodeIntElement(descriptor, 1, value.age)
            }
        }

        override fun deserialize(decoder: Decoder): FormSaved = decoder.decodeStructure(descriptor) {
            var name = ""
            var age = 0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> name = decodeStringElement(descriptor, 0)
                    1 -> age = decodeIntElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index $index")
                }
            }
            FormSaved(name, age)
        }
    }

    /** Keeps what the user typed and leaves out the loaded flag, which the bootstrap sets again. */
    private object FormSaver : SliceSaver<Form, FormSaved> {
        override val serializer: KSerializer<FormSaved> = FormSavedSerializer
        override fun save(state: Form): FormSaved? = FormSaved(state.name, state.age).takeIf { state.name.isNotEmpty() || state.age > 0 }
        override fun restore(saved: FormSaved): Form = Form(name = saved.name, age = saved.age)
    }

    private class FormViewModel(handle: SavedStateHandle) : CapsaViewModel(savedStateHandle = handle) {
        val form: StateFlow<Form> = installSaved(
            capability<Form, FormIntent, Nothing>(name = "form", initial = Form()) {
                bootstrap { state -> Decision(state = state.copy(loaded = true)) }
                onIntent<NameTyped> { state, intent -> Decision(state = state.copy(name = intent.name)) }
                onIntent<AgeTyped> { state, intent -> Decision(state = state.copy(age = intent.age)) }
            },
            FormSaver,
            key = KEY,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theTypedFormSurvivesABundleThroughAParcel() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val vm = FormViewModel(handle)
        vm.send(NameTyped("Zoë ✓"))
        vm.send(AgeTyped(42))
        testScheduler.advanceUntilIdle()

        val saved = handle.savedStateProvider().saveState()
        val restored = FormViewModel(SavedStateHandle.createHandle(saved.throughParcel(), null))

        assertEquals(Form(name = "Zoë ✓", age = 42), restored.form.value)
        testScheduler.advanceUntilIdle()
        assertEquals(Form(name = "Zoë ✓", age = 42, loaded = true), restored.form.value)
    }

    @Test
    fun anUntouchedFormSavesAnEmptyWrapperAndStartsFromTheInitialState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        FormViewModel(handle)
        testScheduler.advanceUntilIdle()

        val saved = handle.savedStateProvider().saveState().throughParcel()
        val restored = FormViewModel(SavedStateHandle.createHandle(saved, null))

        // The key holds a wrapper without a snapshot, so the restored form starts from the initial state.
        assertTrue(checkNotNull(saved.getBundle(KEY)).isEmpty)
        assertEquals(Form(), restored.form.value)
    }

    /** Writes the bundle into a [Parcel], reads it back from the bytes, the way the system keeps it while the process is gone. */
    private fun Bundle.throughParcel(): Bundle {
        val bytes = Parcel.obtain().run {
            try {
                writeBundle(this@throughParcel)
                marshall()
            } finally {
                recycle()
            }
        }
        return Parcel.obtain().run {
            try {
                unmarshall(bytes, 0, bytes.size)
                setDataPosition(0)
                checkNotNull(readBundle(SavedStateOnDeviceTest::class.java.classLoader))
            } finally {
                recycle()
            }
        }
    }

    private companion object {
        const val KEY = "form"
    }
}
