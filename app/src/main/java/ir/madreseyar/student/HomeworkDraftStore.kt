package ir.madreseyar.student

import android.content.Context

class HomeworkDraftStore(context:Context){
    private val store=EncryptedFileStore(context,"homework")
    private fun key(id:String)="hw_$id"
    fun save(id:String,text:String){ if(text.isBlank()) store.delete(key(id)) else store.put(key(id),text) }
    fun load(id:String):String=store.get(key(id))
    fun delete(id:String)=store.delete(key(id))
    fun clear()=store.clear()
}
