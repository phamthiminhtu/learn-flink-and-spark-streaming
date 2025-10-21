#!/usr/bin/env python3
"""
Clickstream Event Producer
Generates realistic e-commerce user behavior and sends to Kafka
"""

import json
import time
import uuid
import random
import os
from datetime import datetime, timedelta
from typing import Dict, List
from dataclasses import dataclass, asdict
from kafka import KafkaProducer
from kafka.errors import KafkaError

# ============================================
# Configuration
# ============================================
KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092').split(',')
TOPIC_NAME = 'clickstream'
EVENTS_PER_SECOND = 10
LATE_DATA_PERCENTAGE = 0.05  # 5% of events arrive late
MAX_LATE_SECONDS = 120  # Up to 2 minutes late

# User behavior simulation
NUM_USERS = 100
NUM_PRODUCTS = 50
SESSION_TIMEOUT_MINUTES = 30
AVG_EVENTS_PER_SESSION = 8

# ============================================
# Data Models
# ============================================
@dataclass
class ClickstreamEvent:
    event_id: str
    user_id: str
    session_id: str
    event_type: str
    product_id: str
    price: float
    event_time: int  # Unix timestamp milliseconds
    processing_time: int  # Unix timestamp milliseconds

    def to_dict(self) -> Dict:
        return asdict(self)

# ============================================
# Session Management
# ============================================
class UserSession:
    """Tracks user session state for realistic behavior"""
    
    def __init__(self, user_id: str):
        self.user_id = user_id
        self.session_id = str(uuid.uuid4())
        self.start_time = datetime.now()
        self.last_event_time = self.start_time
        self.events_in_session = 0
        self.cart_items = set()
        
    def is_expired(self, timeout_minutes: int = 30) -> bool:
        """Check if session has timed out"""
        elapsed = (datetime.now() - self.last_event_time).total_seconds() / 60
        return elapsed > timeout_minutes
    
    def get_next_event_type(self) -> str:
        """Generate realistic event sequence"""
        # First event is always VIEW
        if self.events_in_session == 0:
            return 'VIEW'
        
        # Event transition probabilities (realistic user behavior)
        if len(self.cart_items) == 0:
            # No items in cart: mostly viewing, some add-to-cart
            return random.choices(
                ['VIEW', 'ADD_TO_CART'],
                weights=[0.7, 0.3]
            )[0]
        else:
            # Items in cart: can view more, add more, checkout, or remove
            return random.choices(
                ['VIEW', 'ADD_TO_CART', 'CHECKOUT', 'REMOVE_FROM_CART'],
                weights=[0.4, 0.2, 0.3, 0.1]
            )[0]
    
    def update_state(self, event_type: str, product_id: str):
        """Update session state based on event"""
        self.last_event_time = datetime.now()
        self.events_in_session += 1
        
        if event_type == 'ADD_TO_CART':
            self.cart_items.add(product_id)
        elif event_type == 'REMOVE_FROM_CART':
            self.cart_items.discard(product_id)
        elif event_type == 'CHECKOUT' and len(self.cart_items) > 0:
            # Checkout triggers PURCHASE for all cart items
            return True  # Signal to generate purchase events
        
        return False

# ============================================
# Product Catalog
# ============================================
class ProductCatalog:
    """Simulates product catalog with realistic pricing"""
    
    def __init__(self, num_products: int = 50):
        self.products = {}
        self._generate_catalog(num_products)
    
    def _generate_catalog(self, num_products: int):
        """Generate product catalog with varied pricing"""
        categories = {
            'electronics': (50.0, 500.0),
            'clothing': (15.0, 100.0),
            'books': (10.0, 50.0),
            'home': (20.0, 200.0),
            'sports': (25.0, 150.0)
        }
        
        for i in range(num_products):
            category = random.choice(list(categories.keys()))
            min_price, max_price = categories[category]
            
            self.products[f'PROD_{i:03d}'] = {
                'category': category,
                'price': round(random.uniform(min_price, max_price), 2)
            }
    
    def get_random_product(self) -> tuple:
        """Return (product_id, price)"""
        product_id = random.choice(list(self.products.keys()))
        return product_id, self.products[product_id]['price']

# ============================================
# Event Generator
# ============================================
class EventGenerator:
    """Generates realistic clickstream events"""
    
    def __init__(self, num_users: int, num_products: int):
        self.catalog = ProductCatalog(num_products)
        self.sessions: Dict[str, UserSession] = {}
        self.user_ids = [f'USER_{i:04d}' for i in range(num_users)]
        
    def get_or_create_session(self, user_id: str) -> UserSession:
        """Get existing session or create new one"""
        if user_id not in self.sessions or self.sessions[user_id].is_expired(SESSION_TIMEOUT_MINUTES):
            self.sessions[user_id] = UserSession(user_id)
        return self.sessions[user_id]
    
    def generate_event(self) -> List[ClickstreamEvent]:
        """Generate one or more events (multiple if purchase)"""
        # Pick random user
        user_id = random.choice(self.user_ids)
        session = self.get_or_create_session(user_id)
        
        # Determine event type based on session state
        event_type = session.get_next_event_type()
        
        # Get product
        product_id, price = self.catalog.get_random_product()
        
        # Generate event time (potentially out of order)
        event_time = datetime.now()
        
        # Simulate late data (5% of events)
        if random.random() < LATE_DATA_PERCENTAGE:
            late_seconds = random.randint(10, MAX_LATE_SECONDS)
            event_time -= timedelta(seconds=late_seconds)
        
        processing_time = datetime.now()
        
        # Create main event
        events = [ClickstreamEvent(
            event_id=str(uuid.uuid4()),
            user_id=user_id,
            session_id=session.session_id,
            event_type=event_type,
            product_id=product_id,
            price=price,
            event_time=int(event_time.timestamp() * 1000),
            processing_time=int(processing_time.timestamp() * 1000)
        )]
        
        # Update session state and check for purchases
        should_purchase = session.update_state(event_type, product_id)
        
        if should_purchase:
            # Generate PURCHASE events for all cart items
            for cart_product_id in list(session.cart_items):
                _, cart_price = self.catalog.get_random_product()  # Get price
                events.append(ClickstreamEvent(
                    event_id=str(uuid.uuid4()),
                    user_id=user_id,
                    session_id=session.session_id,
                    event_type='PURCHASE',
                    product_id=cart_product_id,
                    price=cart_price,
                    event_time=int(event_time.timestamp() * 1000),
                    processing_time=int(processing_time.timestamp() * 1000)
                ))
            session.cart_items.clear()
        
        return events

# ============================================
# Kafka Producer
# ============================================
class ClickstreamProducer:
    """Handles Kafka production with metrics"""
    
    def __init__(self, bootstrap_servers: List[str], topic: str):
        self.topic = topic
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            key_serializer=lambda k: k.encode('utf-8') if k else None,
            acks='all',  # Wait for all replicas (durability)
            retries=3,
            compression_type='lz4',
            linger_ms=10,  # Batch events for 10ms (efficiency)
            batch_size=16384
        )
        
        # Metrics
        self.events_sent = 0
        self.events_failed = 0
        self.start_time = time.time()
        
    def send_event(self, event: ClickstreamEvent):
        """Send event to Kafka"""
        try:
            # Use user_id as key for partitioning (events from same user go to same partition)
            future = self.producer.send(
                self.topic,
                key=event.user_id,
                value=event.to_dict()
            )
            
            # Non-blocking: add callback
            future.add_callback(self._on_send_success)
            future.add_errback(self._on_send_error)
            
        except Exception as e:
            print(f"❌ Failed to send event: {e}")
            self.events_failed += 1
    
    def _on_send_success(self, metadata):
        """Callback on successful send"""
        self.events_sent += 1
    
    def _on_send_error(self, exception):
        """Callback on send failure"""
        print(f"❌ Send failed: {exception}")
        self.events_failed += 1
    
    def print_stats(self):
        """Print production statistics"""
        elapsed = time.time() - self.start_time
        rate = self.events_sent / elapsed if elapsed > 0 else 0
        
        print(f"\r📊 Events: {self.events_sent} | Failed: {self.events_failed} | "
              f"Rate: {rate:.1f}/sec | Elapsed: {elapsed:.1f}s", end='', flush=True)
    
    def close(self):
        """Flush and close producer"""
        print("\n🔄 Flushing remaining events...")
        self.producer.flush()
        self.producer.close()
        print("✅ Producer closed")

# ============================================
# Main Producer Loop
# ============================================
def main():
    print("🚀 Starting Clickstream Event Producer")
    print(f"   Target rate: {EVENTS_PER_SECOND} events/sec")
    print(f"   Topic: {TOPIC_NAME}")
    print(f"   Kafka: {KAFKA_BOOTSTRAP_SERVERS}")
    print(f"   Users: {NUM_USERS} | Products: {NUM_PRODUCTS}")
    print(f"   Late data: {LATE_DATA_PERCENTAGE*100}%")
    print()
    
    # Initialize
    generator = EventGenerator(NUM_USERS, NUM_PRODUCTS)
    producer = ClickstreamProducer(KAFKA_BOOTSTRAP_SERVERS, TOPIC_NAME)
    
    try:
        event_count = 0
        while True:
            loop_start = time.time()
            
            # Generate and send events to match target rate
            events = generator.generate_event()
            for event in events:
                producer.send_event(event)
                event_count += 1
            
            # Print stats every 100 events
            if event_count % 100 == 0:
                producer.print_stats()
            
            # Sleep to maintain target rate
            elapsed = time.time() - loop_start
            sleep_time = (1.0 / EVENTS_PER_SECOND) - elapsed
            if sleep_time > 0:
                time.sleep(sleep_time)
    
    except KeyboardInterrupt:
        print("\n\n⚠️  Interrupted by user")
    
    finally:
        producer.close()
        print(f"\n📈 Final Stats:")
        print(f"   Total events: {producer.events_sent}")
        print(f"   Failed: {producer.events_failed}")
        print(f"   Success rate: {(producer.events_sent/(producer.events_sent+producer.events_failed)*100):.1f}%")

if __name__ == "__main__":
    main()