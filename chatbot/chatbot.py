import os
from dotenv import load_dotenv
from google import genai
from Rag import search

# Load API key
load_dotenv()
client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

def get_response(user_input):
    try:
        # 🔍 Get relevant chunks
        relevant_chunks = search(user_input)
        context = "\n\n".join(relevant_chunks)

        prompt = f"""
        You are a helpful assistant for DealLock.

        Answer the question using ONLY the context below.
        If the answer is not in the context, say you don't know.

        Context:
        {context}

        Question:
        {user_input}
        """

        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=prompt
        )

        return response.text

    except Exception as e:
        print("Error:", e)
        return "Sorry, I'm having trouble right now. Please try again later."


#  Local testing mode     
if __name__ == "__main__":
    while True:
        user_input = input("You: ")
        print("Bot:", get_response(user_input))