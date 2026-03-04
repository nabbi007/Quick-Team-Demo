"""ETL Pipeline for QuickPoll Analytics"""
import pandas as pd
from sqlalchemy import create_engine, text
from config import DATABASE_URL

engine = create_engine(DATABASE_URL)

def extract_polls():
    query = text("""
        SELECT p.id, p.question, p.status, p.created_at,
               p.multiple_choice, u.name AS creator_name
        FROM polls p JOIN users u ON p.creator_id = u.id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn)

def extract_votes():
    query = text("""
        SELECT v.id, v.created_at, po.text AS option_text,
               po.poll_id, u.name AS voter_name
        FROM votes v
        JOIN poll_options po ON v.poll_option_id = po.id
        JOIN users u ON v.user_id = u.id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn)

def transform_poll_analytics(polls_df, votes_df):
    """Aggregate vote data per poll."""
    if votes_df.empty:
        return pd.DataFrame()
    vote_counts = votes_df.groupby("poll_id").size().reset_index(name="total_votes")
    result = polls_df.merge(vote_counts, left_on="id", right_on="poll_id", how="left")
    result["total_votes"] = result["total_votes"].fillna(0).astype(int)
    return result[["id", "question", "creator_name", "total_votes", "created_at"]]

def load_analytics(df, table_name):
    df.to_sql(table_name, engine, if_exists="replace", index=False)
    print(f"Loaded {len(df)} rows into {table_name}")

def run_pipeline():
    print("Starting QuickPoll ETL pipeline...")
    polls_df = extract_polls()
    votes_df = extract_votes()
    print(f"Extracted {len(polls_df)} polls, {len(votes_df)} votes")

    analytics = transform_poll_analytics(polls_df, votes_df)
    if not analytics.empty:
        load_analytics(analytics, "analytics_poll_summary")

    # TODO: Add time-series voting trends
    # TODO: Add user participation metrics
    print("ETL pipeline complete!")

if __name__ == "__main__":
    run_pipeline()
