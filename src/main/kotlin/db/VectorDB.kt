package com.dumch.db

import com.dumch.giga.GigaAuth
import com.dumch.giga.GigaChatAPI
import com.dumch.giga.GigaRequest
import org.apache.lucene.document.Document
import org.apache.lucene.document.KnnFloatVectorField
import org.apache.lucene.document.StoredField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnFloatVectorQuery
import kotlin.math.min
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.file.Paths

object VectorDB {
    private const val INDEX_PATH = "build/rag_index"

    init {
        val isInitialized = File(INDEX_PATH).exists() // naive way to check initialization
        if (!isInitialized) {
            val dir = FSDirectory.open(Paths.get(INDEX_PATH))
            IndexWriter(dir, IndexWriterConfig()).use { }
        }
    }

    fun insert(data: List<String>, embeddings: List<List<Double>>) {
        val dir = FSDirectory.open(Paths.get(INDEX_PATH))
        IndexWriter(dir, IndexWriterConfig()).use { writer ->
            data.indices.forEach { idx ->
                val doc = Document()
                doc.add(StoredField("text", data[idx]))
                doc.add(KnnFloatVectorField("embedding", toFloatArray(embeddings[idx])))
                writer.addDocument(doc)
            }
        }
    }

    fun getAllTexts(): List<String> {
        val dir = FSDirectory.open(Paths.get(INDEX_PATH))
        DirectoryReader.open(dir).use { reader ->
            val list = mutableListOf<String>()
            for (i in 0 until reader.maxDoc()) {
                val doc = reader.document(i)
                doc.get("text")?.let { list.add(it) }
            }
            return list
        }
    }

    fun searchSimilar(embedding: List<Double>, limit: Int = 5): List<String> {
        val dir = FSDirectory.open(Paths.get(INDEX_PATH))
        DirectoryReader.open(dir).use { reader ->
            val searcher = IndexSearcher(reader)
            val query = KnnFloatVectorQuery("embedding", toFloatArray(embedding), limit)
            val topDocs = searcher.search(query, limit)
            val texts = mutableListOf<String>()
            topDocs.scoreDocs.forEach { sd ->
                searcher.doc(sd.doc).get("text")?.let { texts.add(it) }
            }
            return texts
        }
    }

    fun clearAllData() {
        val dir = FSDirectory.open(Paths.get(INDEX_PATH))
        IndexWriter(dir, IndexWriterConfig()).use { writer ->
            writer.deleteAll()
        }
    }

    private fun toFloatArray(list: List<Double>): FloatArray {
        val size = min(list.size, MAX_DIM)
        val arr = FloatArray(size)
        for (i in 0 until size) {
            arr[i] = list[i].toFloat()
        }
        return arr
    }

    private const val MAX_DIM = 1024
}

suspend fun main() {
    val vectorDb = VectorDB
    vectorDb.clearAllData()
    val api = GigaChatAPI(GigaAuth)
    val knownFacts = listOf(
        "RAG is an AI technique that combines a search engine with a large language model (LLM) — Google AI overview",
        "Perhaps the biggest and the most obvious problem with frameworks is that they cannot be composed. — Tomas Petricek",
        "Use frameworks only for applications with a short development lifespan, and avoid frameworks for systems you intend to keep for multiple years. — Mathias Verraes",
        "Inversion of control is a common feature of frameworks, but it's something that comes at a price. " +
                "It tends to be hard to understand and leads to problems when you are trying to debug. " +
                "So on the whole I prefer to avoid it unless I need it. — Martin Fowler"
    )
    val factsEmbeddings = api.embeddings(GigaRequest.Embeddings(input = knownFacts))
    vectorDb.insert(knownFacts, factsEmbeddings.data.map { it.embedding })

    val userInput = "Какие проблемы могут возникнуть при использовании фреймворков?"
    val embedding = api.embeddings(GigaRequest.Embeddings(input = listOf(userInput)))
    val result = vectorDb.searchSimilar(embedding.data.first().embedding, limit = 3)
    println(result.joinToString(prefix = "Found:\n", separator = "\n"))
}