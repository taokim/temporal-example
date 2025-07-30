#!/usr/bin/env python3
"""Test script to verify Python setup without running workers"""

import sys
import importlib
import os

def test_imports():
    """Test that all required modules can be imported"""
    print("Testing imports...")
    
    required_modules = [
        'temporalio',
        'aiohttp',
        'aioboto3',
        'asyncpg',
        'pandas',
        'numpy',
        'pydantic',
        'httpx',
    ]
    
    for module in required_modules:
        try:
            importlib.import_module(module)
            print(f"✅ {module}")
        except ImportError as e:
            print(f"❌ {module}: {e}")
            return False
    
    # Test our own modules
    local_modules = [
        'activities.cpu',
        'activities.gpu',
        'activities.io',
        'workflows.resource_optimized',
    ]
    
    for module in local_modules:
        try:
            importlib.import_module(module)
            print(f"✅ {module}")
        except ImportError as e:
            print(f"❌ {module}: {e}")
            return False
    
    return True

def test_env_vars():
    """Test environment variables"""
    print("\nChecking environment variables...")
    
    env_vars = {
        'TEMPORAL_HOST': 'localhost:7233',
        'S3_ENDPOINT': 'http://localhost:9000',
        'S3_ACCESS_KEY': 'minioadmin',
        'S3_SECRET_KEY': 'minioadmin',
        'S3_BUCKET': 'documents',
        'VECTOR_DB_URL': 'http://localhost:8000',
        'METADATA_DB_HOST': 'localhost',
        'METADATA_DB_PORT': '5433',
        'METADATA_DB_NAME': 'document_metadata',
        'METADATA_DB_USER': 'docuser',
        'METADATA_DB_PASSWORD': 'docpass',
        'GPU_COUNT': '2',
    }
    
    missing = []
    for var, default in env_vars.items():
        value = os.environ.get(var, default)
        if value == default and var not in os.environ:
            print(f"⚠️  {var}: using default '{default}'")
        else:
            print(f"✅ {var}: {value}")
    
    return True

def test_csv_file():
    """Test that the CSV file exists"""
    print("\nChecking test data...")
    
    csv_path = "../testdata/documents.csv"
    if os.path.exists(csv_path):
        print(f"✅ CSV file exists: {csv_path}")
        # Count lines
        with open(csv_path, 'r') as f:
            lines = len(f.readlines()) - 1  # Subtract header
        print(f"   Found {lines} documents")
        return True
    else:
        print(f"❌ CSV file not found: {csv_path}")
        return False

def main():
    """Run all tests"""
    print("Python Resource-Optimized Workflow Setup Test")
    print("=" * 50)
    
    # Change to script directory
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    
    all_passed = True
    
    if not test_imports():
        all_passed = False
        print("\n⚠️  Some imports failed. Run: pip install -r requirements.txt")
    
    if not test_env_vars():
        all_passed = False
    
    if not test_csv_file():
        all_passed = False
    
    print("\n" + "=" * 50)
    if all_passed:
        print("✅ All tests passed! Ready to run workers.")
    else:
        print("❌ Some tests failed. Please fix the issues above.")
        sys.exit(1)

if __name__ == "__main__":
    main()