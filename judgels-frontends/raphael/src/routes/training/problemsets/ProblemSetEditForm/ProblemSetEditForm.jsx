import { Button, Intent } from '@blueprintjs/core';
import { Field, reduxForm } from 'redux-form';

import { Required, Slug } from '../../../../components/forms/validations';
import { FormTextInput } from '../../../../components/forms/FormTextInput/FormTextInput';
import { FormTextArea } from '../../../../components/forms/FormTextArea/FormTextArea';

function ProblemSetEditForm({ handleSubmit, submitting, renderFormComponents }) {
  const slugField = {
    name: 'slug',
    label: 'Slug',
    validate: [Required, Slug],
  };
  const nameField = {
    name: 'name',
    label: 'Name',
    validate: [Required],
  };
  const archiveSlugField = {
    name: 'archiveSlug',
    label: 'Archive slug',
    validate: [Required],
  };
  const descriptionField = {
    name: 'description',
    label: 'Description',
    rows: 5,
  };

  const fields = (
    <>
      <Field component={FormTextInput} {...slugField} />
      <Field component={FormTextInput} {...nameField} />
      <Field component={FormTextInput} {...archiveSlugField} />
      <Field component={FormTextArea} {...descriptionField} />
    </>
  );
  const submitButton = <Button type="submit" text="Update" intent={Intent.PRIMARY} loading={submitting} />;

  return <form onSubmit={handleSubmit}>{renderFormComponents(fields, submitButton)}</form>;
}

export default reduxForm({
  form: 'problem-set-edit',
  touchOnBlur: false,
  enableReinitialize: true,
})(ProblemSetEditForm);
