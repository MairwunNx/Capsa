package com.mairwunnx.capsa.samples.profile

import com.mairwunnx.capsa.SliceSaver
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** What the form keeps through the death of the process: the input, not the request or its error. */
@Serializable
data class ProfileSnapshot(val name: String, val email: String)

object ProfileSaver : SliceSaver<ProfileSlice, ProfileSnapshot> {
    override val serializer: KSerializer<ProfileSnapshot> = ProfileSnapshot.serializer()

    override fun save(state: ProfileSlice) = ProfileSnapshot(state.name, state.email)

    override fun restore(saved: ProfileSnapshot) = ProfileSlice(name = saved.name, email = saved.email)
}
