package com.ivy.data.backup

import com.ivy.base.TestDispatchersProvider
import com.ivy.base.di.KotlinxSerializationModule
import com.ivy.data.DataObserver
import com.ivy.data.db.dao.fake.FakeAccountDao
import com.ivy.data.db.dao.fake.FakeBudgetDao
import com.ivy.data.db.dao.fake.FakeCategoryDao
import com.ivy.data.db.dao.fake.FakeLoanDao
import com.ivy.data.db.dao.fake.FakeLoanRecordDao
import com.ivy.data.db.dao.fake.FakePlannedPaymentDao
import com.ivy.data.db.dao.fake.FakeSettingsDao
import com.ivy.data.db.dao.fake.FakeTagAssociationDao
import com.ivy.data.db.dao.fake.FakeTagDao
import com.ivy.data.db.dao.fake.FakeTransactionDao
import com.ivy.data.db.dao.fake.FakeUtilityBillDao
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.CurrencyRepository
import com.ivy.data.repository.RepositoryMemoFactory
import com.ivy.data.repository.fake.fakeRepositoryMemoFactory
import com.ivy.data.repository.mapper.AccountMapper
import com.ivy.data.repository.mapper.CategoryMapper
import com.ivy.data.testResource
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BackupDataUseCaseTest {
    private fun newBackupDataUseCase(
        accountDao: FakeAccountDao = FakeAccountDao(),
        categoryDao: FakeCategoryDao = FakeCategoryDao(),
        transactionDao: FakeTransactionDao = FakeTransactionDao(),
        plannedPaymentDao: FakePlannedPaymentDao = FakePlannedPaymentDao(),
        budgetDao: FakeBudgetDao = FakeBudgetDao(),
        settingsDao: FakeSettingsDao = FakeSettingsDao(),
        loanDao: FakeLoanDao = FakeLoanDao(),
        loanRecordDao: FakeLoanRecordDao = FakeLoanRecordDao(),
        tagDao: FakeTagDao = FakeTagDao(),
        tagAssociationDao: FakeTagAssociationDao = FakeTagAssociationDao(),
        dataObserver: DataObserver = DataObserver(),
    ): BackupDataUseCase {
        val accountMapper = AccountMapper(
            CurrencyRepository(
                settingsDao = settingsDao,
                writeSettingsDao = settingsDao,
                dispatchersProvider = TestDispatchersProvider,
            )
        )
        return BackupDataUseCase(
            accountDao = accountDao,
            accountMapper = accountMapper,
            accountRepository = AccountRepository(
                accountDao = accountDao,
                writeAccountDao = accountDao,
                mapper = accountMapper,
                dispatchersProvider = TestDispatchersProvider,
                memoFactory = fakeRepositoryMemoFactory(),
            ),
            budgetDao = budgetDao,
            categoryDao = categoryDao,
            loanRecordDao = loanRecordDao,
            loanDao = loanDao,
            plannedPaymentRuleDao = plannedPaymentDao,
            transactionDao = transactionDao,
            transactionWriter = transactionDao,
            settingsDao = settingsDao,
            categoryWriter = categoryDao,
            settingsWriter = settingsDao,
            budgetWriter = budgetDao,
            loanWriter = loanDao,
            loanRecordWriter = loanRecordDao,
            plannedPaymentRuleWriter = plannedPaymentDao,

            context = mockk(relaxed = true),
            sharedPrefs = mockk(relaxed = true),
            json = KotlinxSerializationModule.provideJson(),
            dispatchersProvider = TestDispatchersProvider,
            fileSystem = mockk(relaxed = true),
            dataObserver = dataObserver,
            tagsReader = tagDao,
            tagsWriter = tagDao,
            tagAssociationReader = tagAssociationDao,
            tagAssociationWriter = tagAssociationDao,
            utilityBillDao = FakeUtilityBillDao()
        )
    }

    private suspend fun backupTestCase(backupVersion: String) {
        // given
        val originalBackupUseCase = newBackupDataUseCase()
        val backupJsonData = testResource("backups/$backupVersion.json")
            .readText(Charsets.UTF_16)

        // when
        val importedDataRes = originalBackupUseCase.importJson(backupJsonData, onProgress = {})

        // then
        importedDataRes.accountsImported shouldBeGreaterThan 0
        importedDataRes.transactionsImported shouldBeGreaterThan 0
        importedDataRes.categoriesImported shouldBeGreaterThan 0
        importedDataRes.failedRows.size shouldBe 0

        // Also - exporting and re-importing the data should work
        // given
        val exportedJson = originalBackupUseCase.generateJsonBackup()

        // when
        val freshBackupUseCase = newBackupDataUseCase()
        val reImportedDataRes = freshBackupUseCase.importJson(exportedJson, onProgress = {})
        // then
        reImportedDataRes shouldBe importedDataRes

        // Finally, exporting again should yield the same result
        freshBackupUseCase.generateJsonBackup() shouldBe exportedJson
    }

    @Test
    fun `backup compatibility with 450 (150)`() = runTest {
        backupTestCase("450-150")
    }

    @Test
    fun `categories read before an import show up after it`() = runTest {
        // given: the app already looked at the (empty) categories, e.g. on a fresh install
        val categoryDao = FakeCategoryDao()
        val dataObserver = DataObserver()
        val categoryRepository = CategoryRepository(
            mapper = CategoryMapper(),
            writeCategoryDao = categoryDao,
            categoryDao = categoryDao,
            dispatchersProvider = TestDispatchersProvider,
            memoFactory = RepositoryMemoFactory(dataObserver, TestDispatchersProvider),
        )
        categoryRepository.findAll() shouldBe emptyList()
        val useCase = newBackupDataUseCase(categoryDao = categoryDao, dataObserver = dataObserver)
        val backupJsonData = testResource("backups/450-150.json").readText(Charsets.UTF_16)

        // when
        val result = useCase.importJson(backupJsonData, onProgress = {})

        // then
        categoryRepository.findAll().size shouldBe result.categoriesImported
    }
}